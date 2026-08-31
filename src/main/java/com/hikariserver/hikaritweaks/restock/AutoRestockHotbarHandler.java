package com.hikariserver.hikaritweaks.restock;

import com.hikariserver.hikaritweaks.compat.ItemCompat;
import com.hikariserver.hikaritweaks.compat.RegistryCompat;
import com.hikariserver.hikaritweaks.config.TweaksOptions;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ホットバー自動補充ハンドラ。
// コンテナ（チェストなど）を開いたとき、設定リストのアイテムをホットバーへ補充する。
public final class AutoRestockHotbarHandler {
    // コンテナの中身が同期されるのを待つ最大 tick 数。
    // 画面が開いた tick に InventoryS2CPacket がまだ適用されていないと
    // 全スロットが空に見えるため、少しの間だけ再試行する。
    private static final int MAX_SYNC_WAIT_TICKS = 20;

    // 最後に処理した画面（同じ画面で二重処理しないためのキャッシュ）
    private static Screen lastProcessedScreen;
    // 同期待ちで再試行中の画面
    private static Screen pendingScreen;
    // その画面を再試行した tick 数
    private static int pendingTicks;

    // インスタンス化を禁止するプライベートコンストラクタ
    private AutoRestockHotbarHandler() {}

    // 毎 tick 呼ばれるエントリーポイント
    public static void tick(MinecraftClient client) {
        // プレイヤーまたはワールドが存在しない場合はキャッシュをクリアして早期リターン
        if (client.player == null || client.world == null) {
            clearState();
            return;
        }

        // 機能が無効な場合はキャッシュをクリアして早期リターン
        if (!TweaksOptions.AUTO_RESTOCK_HOTBAR.getBooleanValue()) {
            clearState();
            return;
        }

        // コンテナ画面が開いていない場合はキャッシュをクリアして早期リターン
        Screen screen = client.currentScreen;
        if (!(screen instanceof HandledScreen<?> handledScreen)) {
            clearState();
            return;
        }

        // 同じ画面を二重処理しないようにする
        if (screen == lastProcessedScreen) {
            return;
        }

        // 別の画面へ切り替わったら同期待ちのカウンタを戻す
        if (screen != pendingScreen) {
            pendingScreen = screen;
            pendingTicks = 0;
        }

        // プレイヤーインベントリ画面は補充対象外（自分のインベントリだけ開いた場合）
        if (screen instanceof InventoryScreen || handledScreen.getScreenHandler() instanceof PlayerScreenHandler) {
            markProcessed(screen);
            return;
        }

        // 補充処理を実行する。
        // false は「コンテナの中身がまだ 1 つも見えていない ＝ 同期待ちかもしれない」の意味。
        // ここで処理済みにしてしまうと、ラグで InventoryS2CPacket が 1 tick 遅れただけで
        // 「全スロットが空 → 何も補充せず → 二度と見ない」という無言の no-op になる。
        if (process(client, handledScreen)) {
            markProcessed(screen);
        } else if (++pendingTicks >= MAX_SYNC_WAIT_TICKS) {
            // 待っても中身が来ない（本当に空のコンテナ）ので諦めて処理済みにする
            markProcessed(screen);
        }
    }

    // 画面を処理済みとして記録する
    private static void markProcessed(Screen screen) {
        lastProcessedScreen = screen;
        pendingScreen = null;
        pendingTicks = 0;
    }

    // キャッシュと同期待ち状態をすべてクリアする
    private static void clearState() {
        lastProcessedScreen = null;
        pendingScreen = null;
        pendingTicks = 0;
    }

    // コンテナからホットバーへアイテムを補充する本体処理。
    // 「この画面はもう見終わった（再試行しても結果は変わらない）」なら true、
    // 「中身がまだ届いていない可能性があるので次 tick に再試行したい」なら false を返す。
    private static boolean process(MinecraftClient client, HandledScreen<?> handledScreen) {
        ScreenHandler handler = handledScreen.getScreenHandler();
        // コンテナスロットとホットバースロットを分別して取得する
        List<Slot> containerSlots = new ArrayList<>();
        List<Slot> hotbarSlots = new ArrayList<>();

        for (Slot slot : handler.slots) {
            if (slot.inventory instanceof PlayerInventory && slot.getIndex() >= 0 && slot.getIndex() < 9) {
                hotbarSlots.add(slot);
            } else if (!(slot.inventory instanceof PlayerInventory)) {
                containerSlots.add(slot);
            }
        }

        // コンテナかホットバーが空の場合は補充できない
        if (containerSlots.isEmpty() || hotbarSlots.isEmpty()) {
            return true;
        }

        // 本物のコンテナブロックだと確認できない画面には一切触らない
        if (!isTrustedContainer(client, containerSlots.size())) {
            return true;
        }

        // コンテナの中身が 1 つも見えていないなら、まだ同期が来ていない可能性がある。
        // 本当に空のチェストと区別が付かないので、呼び出し側が上限付きで再試行する。
        if (!hasAnyItem(containerSlots)) {
            return false;
        }

        // 補充したアイテム量を表示用にまとめる（表示名 → 個数）
        Map<String, Integer> movedAmounts = new LinkedHashMap<>();
        for (Slot hotbarSlot : hotbarSlots) {
            ItemStack hotbarStack = hotbarSlot.getStack();
            // 空スロットまたは満杯のスロットはスキップする
            if (hotbarStack.isEmpty() || hotbarStack.getCount() >= hotbarStack.getMaxCount()) {
                continue;
            }

            // 補充対象リストに含まれないアイテムはスキップする
            String itemId = RegistryCompat.itemId(hotbarStack.getItem());
            if (!TweaksOptions.HOTBAR_RESTOCK_LIST.getStrings().contains(itemId)) {
                continue;
            }

            // コンテナから該当スロットへ補充を試みる
            int moved = restockSlot(client, handledScreen, hotbarSlot, containerSlots);
            if (moved <= 0) {
                continue;
            }

            // 補充したアイテムをレアリティに応じた色で表示名に変換する
            String displayName = hotbarStack.getName().copy().formatted(getItemFormatting(hotbarStack)).getString();
            movedAmounts.merge(displayName, moved, Integer::sum);
        }

        // 補充があった場合はアクションバーに通知してインベントリを閉じる
        if (!movedAmounts.isEmpty()) {
            client.player.sendMessage(Text.of(buildActionbarMessage(movedAmounts)), true);
            client.player.closeHandledScreen();
        }
        return true;
    }

    // 補充してよいコンテナかどうかを判定する。
    //
    // サーバープラグインのメニュー（GenericContainerScreenHandler をそのまま使った
    // ショップ GUI など）は、クライアントから見ると本物のチェストと完全に同じ形をしている。
    // 区別せずに補充すると、アイコンとして並べられた花火や金のニンジン
    // （どちらも hotbarRestockList の既定値）に PICKUP を撃ってしまう。
    // それは「ショップのボタンを押す」ことに他ならず、所持金が減りうる。
    // しかも補充後は closeHandledScreen() で画面を強制的に閉じるので、
    // 1 tick に数百発の window click を投げたうえで画面が飛ぶ（anti-cheat の kick 対象）。
    //
    // クライアント側に「これは仮想 GUI か？」を直接尋ねる手段は無い。
    // そこで逆に「本物のコンテナである積極的な証拠」を要求し、
    // 証拠が無ければ何もしない（fail closed）方針にする。
    // 補充されないのは軽い不便で済むが、ショップのボタンを押すのは資産を失わせる。
    //
    // 要求する証拠は次の 3 つすべて。
    //   1. クロスヘアがブロックを指しており、そこにクライアントが見えている
    //      BlockEntity があり、それが Inventory である（＝実在する保管ブロック）。
    //      GUI を開いている間はカメラが動かせないので、crosshairTarget は
    //      画面を開く操作をしたブロックを指したままになる
    //      （GameRenderer.renderWorld が毎フレーム updateTargetedEntity を呼ぶが、
    //        視点が固定されている以上ヒット先は変わらない）。
    //      コマンドや NPC・アイテムから開かれた仮想 GUI はここで落ちる。
    //   2. エンダーチェストでないこと。エンダーチェストの中身はプレイヤーごとで、
    //      プラグインが差し替えていることもあり、戻し先が保証できない。
    //      （EnderChestBlockEntity は Inventory を実装していないので条件 1 でも落ちるが、
    //        意図を明示するために独立した条件として残す）
    //   3. 画面のコンテナスロット数がそのブロックの中身のサイズと辻褄が合うこと。
    //      「単チェストを右クリックしたら 54 スロットの GUI が開いた」を検出できる。
    private static boolean isTrustedContainer(MinecraftClient client, int containerSlotCount) {
        if (client.world == null) {
            return false;
        }

        HitResult hit = client.crosshairTarget;
        if (!(hit instanceof BlockHitResult blockHitResult)) {
            return false;
        }

        BlockEntity blockEntity = client.world.getBlockEntity(blockHitResult.getBlockPos());
        if (blockEntity == null) {
            return false;
        }

        // エンダーチェストはスキップする（アイテムが消える危険があるため）
        if (blockEntity instanceof EnderChestBlockEntity) {
            return false;
        }

        // 中身を持つブロックエンティティであること
        if (!(blockEntity instanceof Inventory blockInventory)) {
            return false;
        }

        // チェストだけは隣接してダブルチェストになりうるのでサイズ 2 倍を許す
        boolean allowDouble = blockEntity instanceof ChestBlockEntity;
        return RestockRules.matchesContainerSize(containerSlotCount, blockInventory.size(), allowDouble);
    }

    // コンテナスロットに 1 つでもアイテムが見えているかどうかを返す
    private static boolean hasAnyItem(List<Slot> containerSlots) {
        for (Slot slot : containerSlots) {
            if (!slot.getStack().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    // 1 つのホットバースロットに対してコンテナから補充する
    private static int restockSlot(MinecraftClient client, HandledScreen<?> handledScreen, Slot hotbarSlot, List<Slot> containerSlots) {
        int movedTotal = 0;

        // コンテナの末尾から順に同じアイテムを探して補充する
        for (int i = containerSlots.size() - 1; i >= 0; i--) {
            Slot containerSlot = containerSlots.get(i);
            ItemStack containerStack = containerSlot.getStack().copy();
            ItemStack hotbarStack = hotbarSlot.getStack().copy();

            // どちらかが空の場合はスキップする
            if (containerStack.isEmpty() || hotbarStack.isEmpty()) {
                continue;
            }

            // スタック可能かどうかチェックする（種類・エンチャントなどが一致するか）。
            // ID 一致だけで判定すると ScreenHandler.internalOnSlotClick が
            // 「入れる」ではなく「入れ替える」に落ちるのでこの判定は必須。
            if (!ItemCompat.canCombine(containerStack, hotbarStack)) {
                continue;
            }

            // 移動できる個数はコンテナの個数とホットバーの残り容量の小さい方
            int moveAmount = RestockRules.plannedMoveCount(
                    hotbarStack.getCount(), hotbarStack.getMaxCount(), containerStack.getCount());
            // 満杯なら他のコンテナスロットを見ても入らないので打ち切る
            if (moveAmount <= 0) {
                break;
            }

            moveItems(client, handledScreen, containerSlot, hotbarSlot, moveAmount);
            movedTotal += moveAmount;
        }

        return movedTotal;
    }

    // スロット間でアイテムを移動するためのクリック操作を実行する
    private static void moveItems(MinecraftClient client, HandledScreen<?> handledScreen, Slot containerSlot, Slot hotbarSlot, int moveAmount) {
        ClientPlayerInteractionManager interactionManager = client.interactionManager;
        // interactionManager、player、moveAmount のいずれかが不正なら何もしない
        if (interactionManager == null || client.player == null || moveAmount <= 0) {
            return;
        }

        int syncId = handledScreen.getScreenHandler().syncId;
        // コンテナスロットをピックアップする
        interactionManager.clickSlot(syncId, containerSlot.id, 0, SlotActionType.PICKUP, client.player);
        // ホットバースロットへ 1 個ずつ右クリックで配置する
        for (int i = 0; i < moveAmount; i++) {
            interactionManager.clickSlot(syncId, hotbarSlot.id, 1, SlotActionType.PICKUP, client.player);
        }
        // カーソルに残ったアイテムをコンテナスロットへ戻す
        interactionManager.clickSlot(syncId, containerSlot.id, 0, SlotActionType.PICKUP, client.player);
    }

    // アイテムのレアリティに対応したフォーマット（色）を返す
    private static Formatting getItemFormatting(ItemStack stack) {
        return ItemCompat.rarityFormatting(stack);
    }

    // 補充結果をアクションバー用の文字列に変換する
    private static String buildActionbarMessage(Map<String, Integer> movedAmounts) {
        List<String> contents = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : movedAmounts.entrySet()) {
            contents.add(entry.getKey() + " +" + entry.getValue());
        }
        return "ホットバー自動補充: " + String.join(", ", contents);
    }
}
