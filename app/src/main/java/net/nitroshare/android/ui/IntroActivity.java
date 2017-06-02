package net.nitroshare.android.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.ColorInt;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;

import com.github.paolorotolo.appintro.AppIntro;
import com.github.paolorotolo.appintro.AppIntroBaseFragment;
import com.github.paolorotolo.appintro.AppIntroFragment;

import net.nitroshare.android.R;
import net.nitroshare.android.util.Permissions;

/**
 * Display an interactive introduction to the application
 */
public class IntroActivity extends AppIntro {

    /**
     * Custom fragment that adds a button for visiting the website
     */
    public static class IntroDesktopFragment extends AppIntroBaseFragment {

        @Nullable
        @Override
        public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            View view = super.onCreateView(inflater, container, savedInstanceState);

            //noinspection ConstantConditions
            Button button = (Button) view.findViewById(R.id.websiteButton);
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://nitroshare.net"));
                    startActivity(intent);
                }
            });

            return view;
        }

        public static IntroDesktopFragment newInstance(String title, String description, @DrawableRes int drawable, @ColorInt int bgColor) {
            IntroDesktopFragment fragment = new IntroDesktopFragment();
            Bundle args = new Bundle();
            args.putString(ARG_TITLE, title);
            args.putString(ARG_DESC, description);
            args.putInt(ARG_DRAWABLE, drawable);
            args.putInt(ARG_BG_COLOR, bgColor);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        protected int getLayoutId() {
            return R.layout.fragment_intro_desktop;
        }
    }

    private boolean mShowPermissionSlide;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        super.onCreate(savedInstanceState);

        setBarColor(ContextCompat.getColor(this, R.color.colorPrimaryDark));

        addSlide(AppIntroFragment.newInstance(
                getString(R.string.activity_intro_intro_title),
                getString(R.string.activity_intro_intro_description),
                R.drawable.ic_intro_transfer,
                ContextCompat.getColor(this, R.color.colorPrimary)
        ));

        addSlide(AppIntroFragment.newInstance(
                getString(R.string.activity_intro_share_title),
                getString(R.string.activity_intro_share_description),
                R.drawable.ic_intro_share,
                ContextCompat.getColor(this, R.color.colorPrimary)
        ));

        addSlide(IntroDesktopFragment.newInstance(
                getString(R.string.activity_intro_desktop_title),
                getString(R.string.activity_intro_desktop_description),
                R.drawable.ic_intro_desktop,
                ContextCompat.getColor(this, R.color.colorPrimary)
        ));

        // Determine if the permission slide needs to be shown or not
        mShowPermissionSlide = !Permissions.haveStoragePermission(this);

        // We only need to ask for the permission if the user is running Marshmallow or higher; on
        // previous versions of Android permissions are granted by default
        if (mShowPermissionSlide) {
            addSlide(AppIntroFragment.newInstance(
                    getString(R.string.activity_intro_perms_title),
                    getString(R.string.activity_intro_perms_description),
                    R.drawable.ic_intro_lock,
                    ContextCompat.getColor(this, R.color.colorPrimary)
            ));
        }
    }

    @Override
    public void onSkipPressed(Fragment currentFragment) {
        super.onSkipPressed(currentFragment);

        if (mShowPermissionSlide) {
            pager.setCurrentItem(3);
        } else {
            setResult(RESULT_OK);
            finish();
        }
    }

    @Override
    public void onDonePressed(Fragment currentFragment) {
        super.onDonePressed(currentFragment);

        if (mShowPermissionSlide) {
            Permissions.requestStoragePermission(this);
        } else {
            setResult(RESULT_OK);
            finish();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        if (Permissions.obtainedStoragePermission(requestCode, grantResults)) {
            setResult(RESULT_OK);
            finish();
        }
    }
}
