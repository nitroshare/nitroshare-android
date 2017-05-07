package net.nitroshare.android.ui;

import android.Manifest;
import android.os.Build;
import android.os.Bundle;

import com.heinrichreimersoftware.materialintro.app.IntroActivity;
import com.heinrichreimersoftware.materialintro.slide.SimpleSlide;

import net.nitroshare.android.R;

/**
 * Display an interactive introduction to the application
 */
public class MainIntroActivity extends IntroActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setButtonBackFunction(BUTTON_BACK_FUNCTION_BACK);

        addSlide(new SimpleSlide.Builder()
                .title(R.string.activity_intro_intro_title)
                .description(R.string.activity_intro_intro_description)
                .image(R.drawable.ic_intro_transfer)
                .background(R.color.colorPrimary)
                .backgroundDark(R.color.colorPrimaryDark)
                .build());

        // We only need to ask for the permission if the user is running Marshmallow or higher; on
        // previous versions of Android permissions are granted by default
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            addSlide(new SimpleSlide.Builder()
                    .title(R.string.activity_intro_perms_title)
                    .description(R.string.activity_intro_perms_description)
                    .image(R.drawable.ic_intro_lock)
                    .background(R.color.colorPrimary)
                    .backgroundDark(R.color.colorPrimaryDark)
                    .permission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    .build());
        }
    }
}
