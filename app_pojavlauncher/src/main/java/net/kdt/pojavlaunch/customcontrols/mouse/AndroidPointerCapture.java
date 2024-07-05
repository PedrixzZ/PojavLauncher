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
    private float mLastX = 0, mLastY = 0; // Variáveis para armazenar as últimas posições

    public AndroidPointerCapture(AbstractTouchpad touchpad, View hostView, float scaleFactor) {
        this.mScaleFactor = scaleFactor;
        this.mTouchpad = touchpad;
        this.mHostView = hostView;
        hostView.setOnCapturedPointerListener(this);
        hostView.getViewTreeObserver().addOnWindowFocusChangeListener(this);
    }

    private void enableTouchpadIfNecessary() {
        if (!mTouchpad.getDisplayState()) mTouchpad.enable(true);
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

        // Verifica se o dispositivo é um touchpad
        if ((event.getSource() & InputDevice.SOURCE_CLASS_TRACKBALL) != 0) {
            // Se o dispositivo suporta eixos relativos, use-os
            if (mDeviceSupportsRelativeAxis) {
                // Obtém os valores dos eixos relativos
                float deltaX = event.getAxisValue(MotionEvent.AXIS_RELATIVE_X);
                float deltaY = event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y);

                // Atualiza as posições X e Y
                mLastX += deltaX;
                mLastY += deltaY;

                // Define o vetor de movimento
                mVector[0] = mLastX;
                mVector[1] = mLastY;
            } else {
                // Se o dispositivo não suporta eixos relativos, use as coordenadas absolutas
                mVector[0] = event.getX();
                mVector[1] = event.getY();
            }
        } else {
            // Se não é um trackball, provavelmente é um touchpad e precisa de rastreamento como uma tela sensível ao toque
            mPointerTracker.trackEvent(event);
        }

        // Se o GLFW não está em modo de captura
        if (!CallbackBridge.isGrabbing()) {
            enableTouchpadIfNecessary();

            // Define a escala do movimento do mouse
            mVector[0] *= mMousePrescale;
            mVector[1] *= mMousePrescale;

            // Verifica se há mais de um toque no touchpad
            if (event.getPointerCount() < 2) {
                // Aplica o vetor de movimento ao touchpad
                mTouchpad.applyMotionVector(mVector);
                mScroller.resetScrollOvershoot();
            } else {
                // Executa o scroll
                mScroller.performScroll(mVector);
            }
        } else {
            // Se o GLFW está em modo de captura, atualiza a posição do cursor
            CallbackBridge.mouseX += (mVector[0] * mScaleFactor);
            CallbackBridge.mouseY += (mVector[1] * mScaleFactor);
            CallbackBridge.sendCursorPos(CallbackBridge.mouseX, CallbackBridge.mouseY);
        }

        // Processa o evento de acordo com a ação
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
        if (hasFocus && MainActivity.isAndroid8OrHigher()) mHostView.requestPointerCapture();
    }

    public void detach() {
        mHostView.setOnCapturedPointerListener(null);
        mHostView.getViewTreeObserver().removeOnWindowFocusChangeListener(this);
    }
}
