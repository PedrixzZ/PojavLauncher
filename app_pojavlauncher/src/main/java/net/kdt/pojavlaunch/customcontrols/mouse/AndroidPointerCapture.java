package net.kdt.pojavlaunch.customcontrols.mouse;

import android.os.Build;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;

import androidx.annotation.RequiresApi;

import net.kdt.pojavlaunch.MainActivity;
import net.kdt.pojavlaunch.MinecraftGLSurface;
import net.kdt.pojavlaunch.Tools;

import org.lwjgl.glfw.CallbackBridge;

@RequiresApi(api = Build.VERSION_CODES.O)
public class AndroidPointerCapture implements ViewTreeObserver.OnWindowFocusChangeListener, View.OnCapturedPointerListener {
    private static final float TOUCHPAD_SCROLL_THRESHOLD = 1;
    private final AbstractTouchpad mTouchpad;
    private final View mHostView;
    private final float mScaleFactor;
    private final float mMousePrescale = Tools.dpToPx(1);
    private final PointerTracker mPointerTracker = new PointerTracker();
    private final Scroller mScroller = new Scroller(TOUCHPAD_SCROLL_THRESHOLD);
    private final float[] mVector = mPointerTracker.getMotionVector();

    private int mInputDeviceIdentifier;
    private boolean mDeviceSupportsRelativeAxis;

    public AndroidPointerCapture(AbstractTouchpad touchpad, View hostView, float scaleFactor) {
        this.mScaleFactor = scaleFactor;
        this.mTouchpad = touchpad;
        this.mHostView = hostView;
        hostView.setOnCapturedPointerListener(this);
        hostView.getViewTreeObserver().addOnWindowFocusChangeListener(this);
    }

    private void enableTouchpadIfNecessary() {
        if (!mTouchpad.getDisplayState()) {
            mTouchpad.enable(true);
        }
    }

    public void handleAutomaticCapture() {
        if (!mHostView.hasWindowFocus()) {
            mHostView.requestFocus();
        } else {
            mHostView.requestPointerCapture();
        }
    }

    @Override
    public boolean onCapturedPointer(View view, MotionEvent event) {
        checkSameDevice(event.getDevice());

        // Determine if it's a touchpad or a mouse
        if ((event.getSource() & InputDevice.SOURCE_CLASS_TRACKBALL) != 0) {
            // Se o dispositivo é um trackball, não precisamos de mapeamento de eixos

            // Use as coordenadas absolutas se o dispositivo for um trackball
            mVector[0] = event.getX();
            mVector[1] = event.getY(); 

        } else {
            // Se é um touchpad, precisamos mapear os eixos
            mPointerTracker.trackEvent(event);

            // Obtenha o InputDevice do evento
            InputDevice inputDevice = event.getDevice();

            // Verifique se o dispositivo suporta os eixos relativos
            if (mDeviceSupportsRelativeAxis) {
                // Obtenha o mapeamento de eixos do dispositivo
                int[] axesMapping = inputDevice.getMotionRanges();

                // Mapeie os eixos relativos de acordo com o dispositivo
                for (int i = 0; i < axesMapping.length; i++) {
                    if (axesMapping[i] == MotionEvent.AXIS_RELATIVE_X) {
                        mVector[0] = event.getAxisValue(MotionEvent.AXIS_RELATIVE_X);
                    } else if (axesMapping[i] == MotionEvent.AXIS_RELATIVE_Y) {
                        mVector[1] = event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y);
                    }
                }
            } else {
                // Caso o dispositivo não suporte eixos relativos, use as coordenadas absolutas
                mVector[0] = event.getX();
                mVector[1] = event.getY(); 
            }
        }

        if (!CallbackBridge.isGrabbing()) {
            enableTouchpadIfNecessary();

            // Handle scrolling gestures for multi-touch touchpads.
            mVector[0] *= mMousePrescale;
            mVector[1] *= mMousePrescale;
            if (event.getPointerCount() < 2) {
                mTouchpad.applyMotionVector(mVector);
                mScroller.resetScrollOvershoot();
            } else {
                mScroller.performScroll(mVector);
            }
        } else {
            // Update mouse position for GLFW.
            CallbackBridge.mouseX += (mVector[0] * mScaleFactor);
            CallbackBridge.mouseY += (mVector[1] * mScaleFactor);
            CallbackBridge.sendCursorPos(CallbackBridge.mouseX, CallbackBridge.mouseY);
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                return true;
            case MotionEvent.ACTION_BUTTON_PRESS:
                return MinecraftGLSurface.sendMouseButtonUnconverted(event.getActionButton(), true);
            case MotionEvent.ACTION_BUTTON_RELEASE:
                return MinecraftGLSurface.sendMouseButtonUnconverted(event.getActionButton(), false);
            case MotionEvent.ACTION_SCROLL:
                CallbackBridge.sendScroll(
                        event.getAxisValue(MotionEvent.AXIS_HSCROLL),
                        event.getAxisValue(MotionEvent.AXIS_VSCROLL)
                );
                return true;
            case MotionEvent.ACTION_UP:
                mPointerTracker.cancelTracking();
                return true;
            default:
                return false;
        }
    }

    private void checkSameDevice(InputDevice inputDevice) {
        int newIdentifier = inputDevice.getId();
        if (mInputDeviceIdentifier != newIdentifier) {
            reinitializeDeviceSpecificProperties(inputDevice);
            mInputDeviceIdentifier = newIdentifier;
        }
    }

    private void reinitializeDeviceSpecificProperties(InputDevice inputDevice) {
        mPointerTracker.cancelTracking();
        boolean relativeXSupported = inputDevice.getMotionRange(MotionEvent.AXIS_RELATIVE_X) != null;
        boolean relativeYSupported = inputDevice.getMotionRange(MotionEvent.AXIS_RELATIVE_Y) != null;
        mDeviceSupportsRelativeAxis = relativeXSupported && relativeYSupported;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (hasFocus && MainActivity.isAndroid8OrHigher()) {
            mHostView.requestPointerCapture();
        }
    }

    public void detach() {
        mHostView.setOnCapturedPointerListener(null);
        mHostView.getViewTreeObserver().removeOnWindowFocusChangeListener(this);
    }
}
