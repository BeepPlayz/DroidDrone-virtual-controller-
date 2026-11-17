package de.droiddrone.control;

import android.view.MotionEvent;
import java.util.HashMap;

/**
 * Converts the two on-screen joystick positions into the existing RC input path.
 */
public final class VirtualController {
    private static final int CHANNEL_ROLL = 0;
    private static final int CHANNEL_PITCH = 1;
    private static final int CHANNEL_YAW = 2;
    private static final int CHANNEL_THROTTLE = 3;

    private static final float EPSILON = 0.0001f;

    private final Rc rc;
    private final Config config;

    public VirtualController(Rc rc, Config config) {
        if (rc == null) throw new IllegalArgumentException("rc must not be null");
        if (config == null) throw new IllegalArgumentException("config must not be null");
        this.rc = rc;
        this.config = config;
    }

    /** Updates left-stick yaw and throttle. */
    public void setLeftStick(float yaw, float throttle) {
        HashMap<Integer, Float> values = new HashMap<>();
        putMappedPair(values, CHANNEL_YAW, yaw, CHANNEL_THROTTLE, throttle);
        send(values);
    }

    /** Updates right-stick roll and pitch. */
    public void setRightStick(float roll, float pitch) {
        HashMap<Integer, Float> values = new HashMap<>();
        putMappedPair(values, CHANNEL_ROLL, roll, CHANNEL_PITCH, pitch);
        send(values);
    }

    /** Centres yaw on release and deliberately leaves throttle unchanged. */
    public void releaseLeftStick() {
        HashMap<Integer, Float> values = new HashMap<>();
        putMappedAxis(values, CHANNEL_YAW, 0f, CHANNEL_THROTTLE);
        send(values);
    }

    /** Centres both roll and pitch on release. */
    public void releaseRightStick() {
        HashMap<Integer, Float> values = new HashMap<>();
        putMappedPair(values, CHANNEL_ROLL, 0f, CHANNEL_PITCH, 0f);
        send(values);
    }

    private void putMappedPair(HashMap<Integer, Float> values,
                               int firstChannel, float firstValue,
                               int secondChannel, float secondValue) {
        int firstCode = getMappedCode(firstChannel);
        int secondCode = getMappedCode(secondChannel);

        if (!isAxisCode(firstCode) || !isAxisCode(secondCode)
                || firstCode == secondCode) return;

        values.put(firstCode, toAndroidAxisValue(firstCode, firstValue));
        values.put(secondCode, toAndroidAxisValue(secondCode, secondValue));
    }

    private void putMappedAxis(HashMap<Integer, Float> values,
                               int channel, float value, int channelToPreserve) {
        int code = getMappedCode(channel);
        int preservedCode = getMappedCode(channelToPreserve);

        if (!isAxisCode(code) || code == preservedCode) return;

        values.put(code, toAndroidAxisValue(code, value));
    }

    /**
     * Rc.convertAxisValue expects 0..1 for trigger-style axes,
     * but -1..1 for ordinary Android motion axes.
     */
    private float toAndroidAxisValue(int code, float value) {
        float normalized = clamp(value);

        if (isTriggerAxis(code)) {
            normalized = (normalized + 1f) * 0.5f;
        }

        // Rc.setAxisValues ignores a channel's first exact zero.
        // This tiny offset still rounds to the neutral PWM value (1500).
        if (normalized == 0f) normalized = EPSILON;

        return normalized;
    }

    private boolean isTriggerAxis(int code) {
        return code == MotionEvent.AXIS_BRAKE
                || code == MotionEvent.AXIS_GAS
                || code == MotionEvent.AXIS_LTRIGGER
                || code == MotionEvent.AXIS_RTRIGGER
                || code == MotionEvent.AXIS_THROTTLE;
    }

    private int getMappedCode(int channel) {
        int[] map = config.getRcChannelsMap();
        if (map == null || channel < 0 || channel >= map.length) return -1;
        return map[channel];
    }

    private boolean isAxisCode(int code) {
        return code >= 0 && code < Rc.KEY_CODE_OFFSET;
    }

    private float clamp(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return 0f;
        return Math.max(-1f, Math.min(1f, value));
    }

    private void send(HashMap<Integer, Float> values) {
        if (!values.isEmpty()) rc.setAxisValues(values);
    }
}
