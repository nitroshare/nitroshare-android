package net.nitroshare.android.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.annotation.DrawableRes;
import android.support.annotation.StringRes;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.nitroshare.android.R;

/**
 * Button with a single icon that can be colored
 */
public class TintableButton extends LinearLayout {

    private int mColor;

    private ImageView mImageView;
    private TextView mTextView;

    public TintableButton(Context context, AttributeSet attrs) {
        super(context, attrs);

        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.TintableButton);
        mColor = typedArray.getColor(R.styleable.TintableButton_color,
                ContextCompat.getColor(context, android.R.color.primary_text_dark));
        typedArray.recycle();

        LayoutInflater inflator = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflator.inflate(R.layout.view_tintable_button, this);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mImageView = (ImageView) findViewById(android.R.id.icon);
        mTextView = (TextView) findViewById(android.R.id.text1);

        mImageView.setColorFilter(mColor);
        mTextView.setTextColor(mColor);
    }

    /**
     * Set the icon for the button
     */
    public void setIcon(@DrawableRes int resId) {
        mImageView.setImageResource(resId);
    }

    /**
     * Set the text for the button
     */
    public void setText(@StringRes int resId) {
        mTextView.setText(getContext().getString(resId));
    }
}
