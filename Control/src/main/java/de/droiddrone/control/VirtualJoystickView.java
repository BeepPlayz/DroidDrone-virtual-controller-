package de.droiddrone.control;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

public final class VirtualJoystickView extends View {

    private final VirtualController controller;
    private final View.OnTouchListener originalTouchListener;
    private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int leftPointer = -1;
    private int rightPointer = -1;

    private float leftX, leftY;
    private float rightX, rightY;

    private float leftKnobX, leftKnobY;
    private float rightKnobX, rightKnobY;

    private float radius;
    private float knobRadius;
    private float leftCenterX, rightCenterX, centerY;

    public VirtualJoystickView(
            Context context,
            VirtualController controller,
            View.OnTouchListener originalTouchListener) {

        super(context);

        this.controller = controller;
        this.originalTouchListener = originalTouchListener;

        float density = getResources().getDisplayMetrics().density;
        radius =  sixtyDp(density);
        knobRadius = 25f * density;

        basePaint.setColor(Color.argb(65, 25, 25, 25));
        knobPaint.setColor(Color.argb(175, 225, 225, 225));
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(2f * density);
        ringPaint.setColor(Color.argb(170, 255, 255, 255));

        setClickable(true);
    }

    private float sixtyDp(float density) {
        return 60f * density;
    }

    private void updateCenters() {
        leftCenterX = getWidth() * 0.22f;
        rightCenterX = getWidth() * 0.78f;
        centerY = getHeight() * 0.78f;

        leftX = leftCenterX;
        leftY = centerY;
        rightX = rightCenterX;
        rightY = centerY;

        if (leftPointer == -1) {
            leftKnobX = leftCenterX;
            leftKnobY = centerY;
        }
        if (rightPointer == -1) {
            rightKnobX = rightCenterX;
            rightKnobY = centerY;
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateCenters();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (getWidth() == 0 || getHeight() == 0) return;

        canvas.drawCircle(leftCenterX, centerY, radius, basePaint);
        canvas.drawCircle(leftCenterX, centerY, radius, ringPaint);
        canvas.drawCircle(leftKnobX, leftKnobY, knobRadius, knobPaint);

        canvas.drawCircle(rightCenterX, centerY, radius, basePaint);
        canvas.drawCircle(rightCenterX, centerY, radius, ringPaint);
        canvas.drawCircle(rightKnobX, rightKnobY, knobRadius, knobPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (getWidth() == 0 || getHeight() == 0) return false;

        int action = event.getActionMasked();
        int index = event.getActionIndex();

        if (action == MotionEvent.ACTION_DOWN
                || action == MotionEvent.ACTION_POINTER_DOWN) {
            assignPointer(event, index);
        }

        if (action == MotionEvent.ACTION_MOVE) {
            for (int i = 0; i < event.getPointerCount(); i++) {
                updatePointer(event, i);
            }
        }

        if (action == MotionEvent.ACTION_UP
                || action == MotionEvent.ACTION_POINTER_UP
                || action == MotionEvent.ACTION_CANCEL) {
            releasePointer(event, index, action == MotionEvent.ACTION_CANCEL);
        }

        invalidate();

        if (originalTouchListener != null) {
            originalTouchListener.onTouch(this, event);
        }

        if (action == MotionEvent.ACTION_UP) {
            performClick();
        }

        return true;
    }

    private void assignPointer(MotionEvent event, int index) {
        float x = event.getX(index);
        float y = event.getY(index);
        int id = event.getPointerId(index);

        float leftDistance = distance(x, y, leftCenterX, centerY);
        float rightDistance = distance(x, y, rightCenterX, centerY);

        if (leftPointer == -1 && leftDistance <= radius * 1.8f
                && (rightPointer != -1 || leftDistance <= rightDistance)) {
            leftPointer = id;
            updateLeft(x, y);
        } else if (rightPointer == -1 && rightDistance <= radius * 1.8f) {
            rightPointer = id;
            updateRight(x, y);
        } else if (leftPointer == -1 && leftDistance <= radius * 1.8f) {
            leftPointer = id;
            updateLeft(x, y);
        }
    }

    private void updatePointer(MotionEvent event, int index) {
        int id = event.getPointerId(index);
        float x = event.getX(index);
        float y = event.getY(index);

        if (id == leftPointer) {
            updateLeft(x, y);
        } else if (id == rightPointer) {
            updateRight(x, y);
        }
    }

    private void updateLeft(float x, float y) {
        float dx = x - leftCenterX;
        float dy = y - centerY;
        float[] p = clampPosition(dx, dy);

        leftKnobX = leftCenterX + p[0];
        leftKnobY = centerY + p[1];

        controller.setLeftStick(
                p[0] / radius,
                -p[1] / radius
        );
    }

    private void updateRight(float x, float y) {
        float dx = x - rightCenterX;
        float dy = y - centerY;
        float[] p = clampPosition(dx, dy);

        rightKnobX = rightCenterX + p[0];
        rightKnobY = centerY + p[1];

        controller.setRightStick(
                p[0] / radius,
                -p[1] / radius
        );
    }

    private float[] clampPosition(float dx, float dy) {
        float length = (float) Math.sqrt(dx * dx + dy * dy);

        if (length > radius && length > 0f) {
            float scale = radius / length;
            dx *= scale;
            dy *= scale;
        }

        return new float[]{dx, dy};
    }

    private void releasePointer(MotionEvent event, int index, boolean cancel) {
        if (cancel) {
            if (leftPointer != -1) {
                leftPointer = -1;
                controller.releaseLeftStick();
            }
            if (rightPointer != -1) {
                rightPointer = -1;
                controller.releaseRightStick();
            }
            updateCenters();
            return;
        }

        int id = event.getPointerId(index);

        if (id == leftPointer) {
            leftPointer = -1;
            controller.releaseLeftStick();
            leftKnobX = leftCenterX;
            leftKnobY = centerY;
        }

        if (id == rightPointer) {
            rightPointer = -1;
            controller.releaseRightStick();
            rightKnobX = rightCenterX;
            rightKnobY = centerY;
        }
    }

    private float distance(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2;
        float dy = y1 - y2;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }
}
