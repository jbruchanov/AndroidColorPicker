package com.scurab.android.colorpicker;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

/**
 * Created by jbruchanov on 27/03/2015.
 */
@SuppressWarnings("unused")
public class GradientView extends View {

    public interface OnColorChangedListener {
        void onColorChanged(GradientView view, int color);
    }

    private static final boolean DEBUG = false;

    private static final int[] GRAD_COLORS = new int[]{Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED};
    private static final int[] GRAD_ALPHA = new int[]{Color.WHITE, Color.TRANSPARENT};

    private GradientView mBrightnessGradientView;
    private Shader mShader;
    private Drawable mPointerDrawable;
    private Paint mPaint;
    private Paint mDebugPaint;
    private Paint mPaintBackground;
    private RectF mGradientRect = new RectF();

    private int[] mGradColors = GRAD_COLORS;
    private int[] mGradAlpha = GRAD_ALPHA;
    private float[] mHSV = new float[]{1f, 1f, 1f};

    private int[] mSelectedColorGradient = new int[]{0, Color.BLACK};
    private float mRadius = 0;
    private int mSelectedColor = 0;
    private boolean mIsBrightnessGradient = false;
    private int mLastX = Integer.MIN_VALUE;
    private int mLastY;
    private int mPointerHeight;
    private int mPointerWidth;
    private boolean mLockPointerInBounds = false;

    private OnColorChangedListener mOnColorChangedListener;

    public GradientView(Context context) {
        super(context);
        init(null);
    }

    public GradientView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public GradientView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    @SuppressLint("NewApi")
    public GradientView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(attrs);
    }

    private void init(AttributeSet attrs) {
        setClickable(true);
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaintBackground = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaintBackground.setColor(Color.WHITE);
        setLayerType(View.LAYER_TYPE_SOFTWARE, isInEditMode() ? null : mPaint);
        mRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, getResources().getDisplayMetrics());

        if (DEBUG) {
            mDebugPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mDebugPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20, getResources().getDisplayMetrics()));
        }

        if (attrs != null) {
            TypedArray typedArray = getContext().obtainStyledAttributes(attrs, R.styleable.GradientView);
            for (int i = 0, n = typedArray.getIndexCount(); i < n; i++) {
                int index = typedArray.getIndex(i);
                switch (index) {
                    case R.styleable.GradientView_radius:
                        setRadius(typedArray.getDimensionPixelSize(index, 0));
                        break;
                    case R.styleable.GradientView_pointerDrawable:
                        setPointerDrawable(typedArray.getDrawable(index));
                        break;
                    case R.styleable.GradientView_lockPointerInBounds:
                        setLockPointerInBounds(typedArray.getBoolean(index, false));
                }
            }
            typedArray.recycle();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredWidth = 0;
        int desiredHeight = 0;

        if (mPointerDrawable != null) {
            desiredHeight = mPointerDrawable.getIntrinsicHeight();
            //this is nonsense, but at least have something than 0
            desiredWidth = mPointerDrawable.getIntrinsicWidth();
        }

        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int width;
        int height;

        //Measure Width
        if (widthMode == MeasureSpec.EXACTLY) {
            //Must be this size
            width = widthSize;
        } else if (widthMode == MeasureSpec.AT_MOST) {
            //Can't be bigger than...
            width = Math.min(desiredWidth, widthSize);
        } else {
            //Be whatever you want
            width = desiredWidth;
        }

        //Measure Height
        if (heightMode == MeasureSpec.EXACTLY) {
            //Must be this size
            height = heightSize;
        } else if (heightMode == MeasureSpec.AT_MOST) {
            //Can't be bigger than...
            height = Math.min(desiredHeight, heightSize);
        } else {
            //Be whatever you want
            height = desiredHeight;
        }

        //MUST CALL THIS
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mShader != null) {
            canvas.drawRoundRect(mGradientRect, mRadius, mRadius, mPaintBackground);
            canvas.drawRoundRect(mGradientRect, mRadius, mRadius, mPaint);
        }

        if (DEBUG) {
            String color = "#" + Integer.toHexString(mSelectedColor);
            float width = mPaint.measureText(color);
            mDebugPaint.setColor(mSelectedColor);
            mDebugPaint.setStyle(Paint.Style.FILL);
            canvas.drawRect(0, mGradientRect.bottom - mDebugPaint.getTextSize(), getWidth(), mGradientRect.bottom, mDebugPaint);
            mDebugPaint.setColor(Color.BLACK);
            canvas.drawText(color, 0, mGradientRect.bottom, mDebugPaint);
        }
        onDrawPointer(canvas);

    }

    private void onDrawPointer(Canvas canvas){
        if (mPointerDrawable != null) {
            int vh = getHeight();
            int pwh = mPointerWidth >> 1;
            int phh = mPointerHeight >> 1;
            float tx, ty;
            if (!mIsBrightnessGradient) {
                tx = mLastX - pwh;
                ty = mLastY - phh;
                if (mLockPointerInBounds) {
                    tx = Math.max(mGradientRect.left, Math.min(tx, mGradientRect.right - mPointerWidth));
                    ty = Math.max(mGradientRect.top, Math.min(ty, mGradientRect.bottom - mPointerHeight));
                } else {
                    tx = Math.max(mGradientRect.left - pwh, Math.min(tx, mGradientRect.right - pwh));
                    ty = Math.max(mGradientRect.top - pwh, Math.min(ty, mGradientRect.bottom - phh));
                }
            } else {//vertical lock
                tx = mLastX - pwh;
                ty = mPointerHeight != mPointerDrawable.getIntrinsicHeight() ? (vh >> 1) - phh : 0;
                if (mLockPointerInBounds) {
                    tx = Math.max(mGradientRect.left, Math.min(tx, mGradientRect.right - mPointerWidth));
                    ty = Math.max(mGradientRect.top, Math.min(ty, mGradientRect.bottom - mPointerHeight));
                } else {
                    tx = Math.max(mGradientRect.left - pwh, Math.min(tx, mGradientRect.right - pwh));
                    ty = Math.max(mGradientRect.top - pwh, Math.min(ty, mGradientRect.bottom - phh));
                }
            }
            canvas.translate(tx, ty);
            mPointerDrawable.draw(canvas);
            canvas.translate(-tx, -ty);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        mGradientRect.set(getPaddingLeft(), getPaddingTop(), right - left - getPaddingRight(), bottom - top - getPaddingBottom());
        if (changed) {
            buildShaders();
        }

        if (mPointerDrawable != null) {
            int h = (int) mGradientRect.height();
            int ph = mPointerDrawable.getIntrinsicHeight();
            int pw = mPointerDrawable.getIntrinsicWidth();
            mPointerHeight = ph;
            mPointerWidth = pw;
            if (h < ph) {
                mPointerHeight = h;
                mPointerWidth = (int) (pw * (h / (float)ph));
            }
            mPointerDrawable.setBounds(0, 0, mPointerWidth, mPointerHeight);
            updatePointerPosition();
        }
    }

    private void buildShaders() {
        if (mIsBrightnessGradient) {
            mShader = new LinearGradient(mGradientRect.left, mGradientRect.top, mGradientRect.right, mGradientRect.top /* simple line gradient*/, mSelectedColorGradient, null, Shader.TileMode.CLAMP);
        } else {
            LinearGradient gradientShader = new LinearGradient(mGradientRect.left, mGradientRect.top, mGradientRect.right, mGradientRect.top /* simple line gradient*/, GRAD_COLORS, null, Shader.TileMode.CLAMP);
            LinearGradient alphaShader = new LinearGradient(0, mGradientRect.top + (mGradientRect.height() / 3) /* don't start at 0px*/, 0, mGradientRect.bottom, GRAD_ALPHA, null, Shader.TileMode.CLAMP);
            mShader = new ComposeShader(alphaShader, gradientShader, PorterDuff.Mode.MULTIPLY);
        }
        mPaint.setShader(mShader);
    }

    /**
     * Set radius for gradient rectangle
     * @param radius
     */
    public void setRadius(float radius) {
        if(radius != mRadius) {
            mRadius = radius;
            mRadius = radius;
            invalidate();
        }
    }

    public float getRadius() {
        return mRadius;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        mLastX = (int) event.getX();
        mLastY = (int) event.getY();
        onUpdateColorSelection(mLastX, mLastY);
        invalidate();
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                getParent().requestDisallowInterceptTouchEvent(false);
                break;
        }
        return super.onTouchEvent(event);
    }

    /**
     * Update color based on touch events
     * @param x
     * @param y
     */
    protected void onUpdateColorSelection(int x, int y) {
        x = (int) Math.max(mGradientRect.left, Math.min(x, mGradientRect.right));
        y = (int) Math.max(mGradientRect.top, Math.min(y, mGradientRect.bottom));
        if (mIsBrightnessGradient) {
            float b = pointToValueBrightness(x);
            mHSV[2] = b;
            mSelectedColor = Color.HSVToColor(mHSV);
        } else {
            float hue = pointToHue(x);
            float sat = pointToSaturation(y);
            mHSV[0] = hue;
            mHSV[1] = sat;
            mSelectedColor = Color.HSVToColor(mHSV);
        }
        dispatchColorChanged(mSelectedColor);
        String color = Integer.toHexString(mSelectedColor);
    }

    protected void dispatchColorChanged(int color) {
        if (mBrightnessGradientView != null) {
            mBrightnessGradientView.setColor(color, false);
        }
        if (mOnColorChangedListener != null) {
            mOnColorChangedListener.onColorChanged(this, color);
        }
    }

    /**
     * Switch view into brightness gradient only
     * @param isBrightnessGradient
     */
    public void setIsBrightnessGradient(boolean isBrightnessGradient) {
        mIsBrightnessGradient = isBrightnessGradient;
    }

    /**
     * Get current selectec color
     * @return
     */
    public int getSelectedColor() {
        return mSelectedColor;
    }

    /**
     * Update current color
     * @param selectedColor
     */
    public void setColor(int selectedColor) {
        setColor(selectedColor, true);
    }

    protected void setColor(int selectedColor, boolean updatePointers) {
        Color.colorToHSV(selectedColor, mHSV);
        if (mIsBrightnessGradient) {
            mSelectedColorGradient[0] = selectedColor;
            mSelectedColor = Color.HSVToColor(mHSV);
            buildShaders();
            if (mLastX != Integer.MIN_VALUE) {
                mHSV[2] = pointToValueBrightness(mLastX);
            }
            selectedColor = Color.HSVToColor(mHSV);
        }
        if (updatePointers) {
            updatePointerPosition();
        }
        mSelectedColor = selectedColor;
        invalidate();
        dispatchColorChanged(mSelectedColor);
    }

    private void updatePointerPosition() {
        if (mGradientRect.width() != 0 && mGradientRect.height() != 0) {
            if (!mIsBrightnessGradient) {
                mLastX = hueToPoint(mHSV[0]);
                mLastY = saturationToPoint(mHSV[1]);
            } else {
                mLastX = brightnessToPoint(mHSV[2]);
            }
        }
    }

    /**
     * Add reference for brightness view
     * @param brightnessGradient
     */
    public void setBrightnessGradientView(GradientView brightnessGradient) {
        if (mBrightnessGradientView != brightnessGradient) {
            mBrightnessGradientView = brightnessGradient;

            if (mBrightnessGradientView != null) {
                mBrightnessGradientView.setIsBrightnessGradient(true);
                mBrightnessGradientView.setColor(mSelectedColor);
            }
        }
    }

    public void setOnColorChangedListener(OnColorChangedListener onColorChangedListener) {
        mOnColorChangedListener = onColorChangedListener;
    }

    //region HSL math
    /**
     *
     * @param x x coordinate of gradient
     * @return
     */
    private float pointToHue(float x) {
        x = x - mGradientRect.left;
        return x * 360f / mGradientRect.width();
    }

    private int hueToPoint(float hue) {
        return (int)(mGradientRect.left + ((hue * mGradientRect.width()) / 360));
    }

    /**
     * Get saturation
     *
     * @param y
     * @return
     */
    private float pointToSaturation(float y) {
        y = y - mGradientRect.top;
        return 1 - (1.f / mGradientRect.height() * y);
    }

    private int saturationToPoint(float sat) {
        sat = 1 - sat;
        return (int) (mGradientRect.top + (mGradientRect.height() * sat));
    }

    /**
     * Get value of brightness
     *
     * @param x
     * @return
     */
    private float pointToValueBrightness(float x) {
        x = x - mGradientRect.left;
        return 1 - (1.f / mGradientRect.width() * x);
    }

    private int brightnessToPoint(float val) {
        val = 1 - val;
        return (int) (mGradientRect.left + (mGradientRect.width() * val));
    }
    //endregion HSL math

    public void setPointerDrawable(Drawable pointerDrawable) {
        if (mPointerDrawable != pointerDrawable) {
            mPointerDrawable = pointerDrawable;
            requestLayout();
        }
    }

    public void setLockPointerInBounds(boolean lockPointerInBounds) {
        if (lockPointerInBounds != mLockPointerInBounds) {
            mLockPointerInBounds = lockPointerInBounds;
            invalidate();
        }
    }

    public boolean isLockedPointerInBounds() {
        return mLockPointerInBounds;
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        SavedState ss = new SavedState(superState);
        ss.isBrightnessGradient = mIsBrightnessGradient;
        ss.color = mSelectedColor;
        return ss;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if(!(state instanceof SavedState)) {
            super.onRestoreInstanceState(state);
            return;
        }

        SavedState ss = (SavedState)state;
        super.onRestoreInstanceState(ss.getSuperState());

        mIsBrightnessGradient = ss.isBrightnessGradient;
        setColor(ss.color, true);
    }

    private static class SavedState extends BaseSavedState {
        int color;
        boolean isBrightnessGradient;

        SavedState(Parcelable superState) {
            super(superState);
        }

        private SavedState(Parcel in) {
            super(in);
            color = in.readInt();
            isBrightnessGradient = in.readInt() == 1;
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(color);
            out.writeInt(isBrightnessGradient ? 1 : 0);
        }

        //required field that makes Parcelables from a Parcel
        public static final Parcelable.Creator<SavedState> CREATOR =
                new Parcelable.Creator<SavedState>() {
                    public SavedState createFromParcel(Parcel in) {
                        return new SavedState(in);
                    }

                    public SavedState[] newArray(int size) {
                        return new SavedState[size];
                    }
                };
    }
}
