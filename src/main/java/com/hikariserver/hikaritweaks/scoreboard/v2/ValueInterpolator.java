package com.hikariserver.hikaritweaks.scoreboard.v2;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class ValueInterpolator {
    private static final double TIME_CONSTANT_SECONDS = 0.12;

    private static final double MAX_STEP_SECONDS = 0.5;

    private static final class State {
        double current;
        long   lastNanos;
        long   frame;

        State(long target, long nowNanos, long frame) {
            this.current   = target;
            this.lastNanos = nowNanos;
            this.frame     = frame;
        }
    }

    private final Map<UUID, State> states = new HashMap<>();

    private State totalState;

    private long frame;

    public long display(UUID playerId, long target, long nowNanos) {
        State s = states.get(playerId);
        if (s == null) {
            states.put(playerId, new State(target, nowNanos, frame));
            return target;
        }
        return advance(s, target, nowNanos);
    }

    public long displayTotal(long target, long nowNanos) {
        if (totalState == null) {
            totalState = new State(target, nowNanos, frame);
            return target;
        }
        return advance(totalState, target, nowNanos);
    }

    private long advance(State s, long target, long nowNanos) {
        double dt = (nowNanos - s.lastNanos) / 1_000_000_000.0;
        s.lastNanos = nowNanos;
        s.frame     = frame;

        if (dt > MAX_STEP_SECONDS) {
            s.current = target;
            return target;
        }
        if (dt > 0.0) {
            double factor = 1.0 - Math.exp(-dt / TIME_CONSTANT_SECONDS);
            s.current += (target - s.current) * factor;
        }

        if (Math.abs(target - s.current) < 1.0) {
            s.current = target;
            return target;
        }
        return Math.round(s.current);
    }

    public void endFrame() {
        Iterator<Map.Entry<UUID, State>> it = states.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().frame != frame) it.remove();
        }
        if (totalState != null && totalState.frame != frame) totalState = null;
        frame++;
    }

    public void reset() {
        states.clear();
        totalState = null;
    }

    public int trackedRows() {
        return states.size();
    }

    public boolean trackingTotal() {
        return totalState != null;
    }
}
