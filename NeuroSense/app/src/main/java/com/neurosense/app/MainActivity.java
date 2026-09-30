package com.neurosense.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ActivityNotFoundException;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ImageSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class MainActivity extends Activity {
    private static final String DEVICE_NAME = "NeuroVibe";
    private static final int MAX_VALUE = 230;
    private static final int PERMISSION_REQUEST = 41;
    private static final int EXPORT_XLSX_REQUEST = 73;
    private static final int PICK_PATIENT_REPORTS_REQUEST = 74;
    private static final int CAPTURE_PATIENT_REPORT_REQUEST = 75;
    private static final long SCAN_TIMEOUT_MS = 12_000L;
    private static final String ADMIN_PASSWORD = "neurovibe123";

    private static final UUID SERVICE_UUID =
            UUID.fromString("7b3a0001-6f3b-4b5d-9a2e-0f6d4c2b1a00");
    private static final UUID MOTOR_COMMAND_UUID =
            UUID.fromString("7b3a0002-6f3b-4b5d-9a2e-0f6d4c2b1a00");

    private static final int BG = Color.rgb(247, 247, 251);
    private static final int WHITE = Color.WHITE;
    private static final int PRIMARY = Color.rgb(103, 85, 217);
    private static final int LAVENDER = Color.rgb(124, 106, 230);
    private static final int PRIMARY_CONTAINER = Color.rgb(237, 233, 255);
    private static final int SURFACE_LOW = Color.rgb(243, 242, 248);
    private static final int SURFACE_HIGH = Color.rgb(246, 244, 255);
    private static final int TEXT = Color.rgb(28, 27, 43);
    private static final int TEXT_MUTED = Color.rgb(99, 96, 116);
    private static final int MUTED = Color.rgb(135, 131, 151);
    private static final int OUTLINE = Color.rgb(227, 225, 235);
    private static final int SUCCESS = Color.rgb(52, 199, 89);
    private static final int CAUTION = Color.rgb(197, 138, 50);
    private static final int ERROR = Color.rgb(255, 59, 48);
    private static final int ERROR_LIGHT = Color.rgb(255, 239, 241);

    private static final int PAGE_DEVICE = 0;
    private static final int PAGE_CONTROL = 1;
    private static final int PAGE_PRESETS = 2;
    private static final int PAGE_HELP = 3;
    private static final int PAGE_SETTINGS = 4;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner scanner;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic motorCharacteristic;
    private boolean scanning;
    private boolean connecting;
    private boolean connected;
    private boolean deviceNotFound;
    private boolean communicationError;
    private int lastRssi;
    private int currentMotorValue;
    private int lastSentValue = -1;
    private int currentPage = PAGE_DEVICE;
    private int selectedPresetValue = -1;
    private String selectedPresetName;
    private int onboardingStep;
    private boolean showCustomPresets;

    private FrameLayout pageContainer;
    private LinearLayout topStatusPill;
    private ImageView topStatusIcon;
    private TextView topConnectionText;
    private final LinearLayout[] navItems = new LinearLayout[5];
    private final ImageView[] navIcons = new ImageView[5];
    private final TextView[] navLabels = new TextView[5];
    private TextView frequencyText;
    private TextView percentText;
    private TextView activeStateText;
    private CapsuleSliderView motorSlider;
    private CircularDialView motorDial;
    private PulseRingView pulseRing;

    private SharedPreferences preferences;
    private AdminDataStore adminDataStore;
    private PtpActivityStore ptpActivityStore;
    private boolean adminUnlocked;
    private boolean adminWorkspaceVisible;
    private String selectedAdminPatientId;
    private String activeAdminPatientId;
    private AdminDataStore.SessionRecord activeAdminSession;
    private TextView adminDurationText;
    private int adminPage;
    private boolean adminSessionSetupActive;
    private String pendingAdminSessionNumber;
    private String pendingAdminSessionObjective;
    private int adminTestFrequency = 60;
    private int adminDraftFrequency = 60;
    private byte[] pendingExportBytes;
    private String pendingExportName;
    private final List<AdminDataStore.ReportAttachment> pendingPatientReports =
            new ArrayList<>();
    private TextView pendingPatientReportsText;
    private String pendingPatientCameraPath;
    private boolean ptpEnabled;
    private String ptpPatientName = "";
    private String ptpPatientPhone = "";
    private int ptpLowFrequency = 50;
    private int ptpMediumFrequency = 100;
    private int ptpHighFrequency = 150;

    private final Runnable adminDurationTick = new Runnable() {
        @Override
        public void run() {
            if (activeAdminSession == null || adminDurationText == null) {
                return;
            }
            adminDurationText.setText(formatDuration(activeAdminSession.durationMillis()));
            handler.postDelayed(this, 1_000L);
        }
    };

    private final Runnable scanTimeout = () -> {
        if (scanning) {
            stopScan();
            deviceNotFound = true;
            Toast.makeText(this,
                    "NeuroVibe was not found. Check its power and distance.",
                    Toast.LENGTH_LONG).show();
            renderCurrentPage();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences("neurosense_local", MODE_PRIVATE);
        ptpEnabled = preferences.getBoolean("ptp_enabled", false);
        ptpPatientName = preferences.getString("ptp_patient_name", "");
        ptpPatientPhone = preferences.getString("ptp_patient_phone", "");
        ptpLowFrequency = preferences.getInt("ptp_low_frequency", 50);
        ptpMediumFrequency = preferences.getInt("ptp_medium_frequency", 100);
        ptpHighFrequency = preferences.getInt("ptp_high_frequency", 150);
        adminDataStore = new AdminDataStore(preferences);
        ptpActivityStore = new PtpActivityStore(preferences);

        BluetoothManager manager = getSystemService(BluetoothManager.class);
        bluetoothAdapter = manager == null ? null : manager.getAdapter();

        if (preferences.getBoolean("welcome_seen", false)) {
            buildAppShell();
            showPage(PAGE_DEVICE);
        } else {
            showWelcomeScreen();
        }

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "A nearby device connection is not available on this phone.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void showWelcomeScreen() {
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(WHITE);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        final FrameLayout screen = new FrameLayout(this);
        screen.setBackgroundColor(BG);

        int[] images = {
                R.drawable.hero_lifestyle,
                R.drawable.hero_device_close,
                R.drawable.hero_device_full
        };
        String[] descriptions = {
                "Person relaxing while using the NeuroVibe device",
                "Close view of the NeuroVibe device",
                "NeuroVibe device"
        };
        String[] chips = {
                "PRIVATE BY DESIGN",
                "PRIVATE CONNECTION",
                "SAFETY BUILT IN"
        };
        String[] titles = {
                "Meet NeuroVibe",
                "Pair in seconds",
                "Relax with confidence"
        };
        String[] bodies = {
                "A calm, focused way to use NeuroVibe directly from your phone.",
                "Connect nearby without an account. Your controls and preferences remain on this device.",
                "The session stops safely if the connection is interrupted, and Stop is always one tap away."
        };
        String[][] featureTitles = {
                {"Simple control", "Completely local"},
                {"Nearby Devices only", "No cloud or tracking"},
                {"Automatic safe stop", "Immediate Stop control"}
        };
        String[][] featureDetails = {
                {"One consistent experience", "No account is required"},
                {"Used only to find NeuroVibe", "Nothing is uploaded"},
                {"Triggered after a disconnect", "Available during every session"}
        };
        int[][] featureIcons = {
                {R.drawable.ic_tune, R.drawable.ic_brain_circuit},
                {R.drawable.ic_signal, R.drawable.ic_brain_circuit},
                {R.drawable.ic_stop, R.drawable.ic_signal}
        };

        ImageView background = new ImageView(this);
        background.setImageResource(images[onboardingStep]);
        background.setScaleType(ImageView.ScaleType.CENTER_CROP);
        background.setContentDescription(descriptions[onboardingStep]);
        screen.addView(background, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        GradientDrawable shade = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.argb(70, 16, 10, 36), Color.TRANSPARENT,
                        Color.argb(105, 18, 12, 38)});
        View scrim = new View(this);
        scrim.setBackground(shade);
        screen.addView(scrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setPadding(dp(22), dp(38), dp(22), dp(18));

        LinearLayout topBar = new LinearLayout(this);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        Button back = plainButton(onboardingStep == 0 ? "NeuroSense" : "‹ Back");
        back.setTextColor(WHITE);
        back.setBackground(rippleBackground(Color.argb(70, 20, 14, 40), 99));
        back.setEnabled(onboardingStep > 0);
        back.setAlpha(1f);
        if (onboardingStep > 0) {
            back.setOnClickListener(v -> transitionOnboarding(screen,
                    onboardingStep - 1, true));
        }
        attachPressAnimation(back);
        topBar.addView(back, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)));

        TextView step = text("STEP " + (onboardingStep + 1) + " OF 3",
                11, WHITE, Typeface.BOLD);
        step.setLetterSpacing(0.12f);
        step.setGravity(Gravity.CENTER);
        step.setPadding(dp(12), dp(8), dp(12), dp(8));
        step.setBackground(roundRect(Color.argb(70, 20, 14, 40),
                99, Color.TRANSPARENT, 0));
        LinearLayout.LayoutParams stepParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        stepParams.setMargins(dp(10), 0, dp(10), 0);
        topBar.addView(step, stepParams);

        Button skip = plainButton("Skip");
        skip.setTextColor(WHITE);
        skip.setBackground(rippleBackground(Color.argb(70, 20, 14, 40), 99));
        skip.setOnClickListener(v -> finishOnboarding());
        attachPressAnimation(skip);
        topBar.addView(skip, new LinearLayout.LayoutParams(dp(68), dp(44)));
        overlay.addView(topBar);

        Space imageSpace = new Space(this);
        overlay.addView(imageSpace, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(22), dp(20), dp(22), dp(18));
        sheet.setBackground(roundRect(Color.rgb(252, 251, 255),
                30, Color.argb(35, 103, 85, 217), 1));
        sheet.setElevation(dp(14));

        LinearLayout sheetHeader = new LinearLayout(this);
        sheetHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView chip = text(chips[onboardingStep], 10, PRIMARY, Typeface.BOLD);
        chip.setLetterSpacing(0.12f);
        chip.setPadding(dp(11), dp(6), dp(11), dp(6));
        chip.setBackground(roundRect(PRIMARY_CONTAINER, 99,
                Color.TRANSPARENT, 0));
        sheetHeader.addView(chip, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        Space headerSpace = new Space(this);
        sheetHeader.addView(headerSpace, new LinearLayout.LayoutParams(
                0, dp(1), 1f));
        LinearLayout dots = new LinearLayout(this);
        dots.setGravity(Gravity.CENTER_VERTICAL);
        for (int index = 0; index < 3; index++) {
            View dot = new View(this);
            dot.setBackground(roundRect(index == onboardingStep ? PRIMARY
                    : Color.rgb(211, 209, 220), 99, Color.TRANSPARENT, 0));
            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(
                    dp(index == onboardingStep ? 22 : 7), dp(7));
            dotParams.setMargins(dp(3), 0, dp(3), 0);
            dots.addView(dot, dotParams);
        }
        sheetHeader.addView(dots);
        sheet.addView(sheetHeader);

        TextView heading = text(titles[onboardingStep], 30, TEXT, Typeface.BOLD);
        addWithTop(sheet, heading, 12);
        TextView body = text(bodies[onboardingStep], 15, TEXT_MUTED, Typeface.NORMAL);
        addWithTop(sheet, body, 6);

        LinearLayout features = new LinearLayout(this);
        features.setOrientation(LinearLayout.HORIZONTAL);
        for (int index = 0; index < 2; index++) {
            LinearLayout feature = onboardingFeature(
                    featureIcons[onboardingStep][index],
                    featureTitles[onboardingStep][index],
                    featureDetails[onboardingStep][index],
                    onboardingStep == 2 && index == 0 ? ERROR : PRIMARY);
            LinearLayout.LayoutParams featureParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (index == 1) {
                featureParams.setMargins(dp(8), 0, 0, 0);
            }
            features.addView(feature, featureParams);
        }
        addWithTop(sheet, features, 15);

        Button next = primaryButton(onboardingStep == 2
                ? "Start using NeuroSense" : "Continue");
        setButtonIcon(next, onboardingStep == 2
                ? R.drawable.ic_play : R.drawable.ic_arrow_forward, WHITE);
        next.setOnClickListener(v -> {
            if (onboardingStep < 2) {
                transitionOnboarding(screen, onboardingStep + 1, false);
            } else {
                screen.animate().alpha(0f).setDuration(180)
                        .withEndAction(this::finishOnboarding).start();
            }
        });
        attachPressAnimation(next);
        addWithTop(sheet, next, 16);

        Button safety = plainButton(onboardingStep == 2
                ? "Read full safety guidance" : "Safety and responsible use");
        safety.setOnClickListener(v -> showSafetyDetails());
        attachPressAnimation(safety);
        addWithTop(sheet, safety, 3);

        overlay.addView(sheet, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        screen.addView(overlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(screen);
        animateOnboardingEntrance(background, sheet, topBar);
    }

    private LinearLayout onboardingFeature(int iconResource, String title,
                                           String detail, int accent) {
        LinearLayout feature = new LinearLayout(this);
        feature.setOrientation(LinearLayout.VERTICAL);
        feature.setPadding(dp(12), dp(11), dp(12), dp(11));
        feature.setBackground(roundRect(SURFACE_HIGH, 16,
                Color.TRANSPARENT, 0));
        ImageView featureIcon = icon(iconResource, accent, 18);
        feature.addView(featureIcon, new LinearLayout.LayoutParams(dp(25), dp(25)));
        addWithTop(feature, text(title, 12, TEXT, Typeface.BOLD), 6);
        addWithTop(feature, text(detail, 10, TEXT_MUTED, Typeface.NORMAL), 2);
        return feature;
    }

    private void transitionOnboarding(View screen, int targetStep, boolean reverse) {
        screen.animate()
                .alpha(0f)
                .translationX(reverse ? dp(28) : -dp(28))
                .setDuration(180)
                .withEndAction(() -> {
                    onboardingStep = targetStep;
                    showWelcomeScreen();
                })
                .start();
    }

    private void animateOnboardingEntrance(ImageView image, View sheet, View topBar) {
        image.setScaleX(1.045f);
        image.setScaleY(1.045f);
        image.setAlpha(0.72f);
        image.animate().scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(650).start();
        sheet.setTranslationY(dp(52));
        sheet.setAlpha(0f);
        sheet.animate().translationY(0f).alpha(1f)
                .setStartDelay(90).setDuration(420).start();
        topBar.setTranslationY(-dp(18));
        topBar.setAlpha(0f);
        topBar.animate().translationY(0f).alpha(1f)
                .setStartDelay(120).setDuration(340).start();
    }

    private void attachPressAnimation(View view) {
        view.setOnTouchListener((pressedView, event) -> {
            if (!pressedView.isEnabled()) {
                return false;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                pressedView.animate().scaleX(0.975f).scaleY(0.975f)
                        .setDuration(90).start();
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP ||
                    event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                springScale(pressedView, 1f);
            }
            return false;
        });
    }

    private void finishOnboarding() {
        preferences.edit().putBoolean("welcome_seen", true).apply();
        buildAppShell();
        showPage(PAGE_DEVICE);
    }

    private void showSafetyDetails() {
        new AlertDialog.Builder(this)
                .setTitle("Safety by design")
                .setMessage("NeuroSense ends the session whenever you press Stop, the connection is interrupted, communication fails, or the app leaves the foreground.\n\nStart low and stop immediately if discomfort occurs. NeuroSense is a wellness app and does not diagnose or treat a medical condition.")
                .setPositiveButton("Understood", null)
                .show();
    }

    private void buildAppShell() {
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(WHITE);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                        View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        root.addView(buildTopBar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(76)));

        pageContainer = new FrameLayout(this);
        root.addView(pageContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        root.addView(buildBottomNavigation(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(78)));
        setContentView(root);
    }

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(20), dp(12), dp(16), dp(10));
        bar.setBackgroundColor(BG);

        ImageView mark = icon(R.drawable.ic_brain_circuit, PRIMARY, 27);
        mark.setContentDescription("NeuroSense logo");
        bar.addView(mark, new LinearLayout.LayoutParams(dp(42), dp(46)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView title = text("NeuroSense", 19, TEXT, Typeface.BOLD);
        topStatusPill = new LinearLayout(this);
        topStatusPill.setOrientation(LinearLayout.HORIZONTAL);
        topStatusPill.setGravity(Gravity.CENTER_VERTICAL);
        topStatusPill.setPadding(dp(7), dp(2), dp(9), dp(2));
        topStatusPill.setBackground(roundRect(SURFACE_LOW, 99, OUTLINE, 1));
        topStatusIcon = icon(R.drawable.ic_signal, TEXT_MUTED, 11);
        topConnectionText = text("DISCONNECTED", 10, TEXT_MUTED, Typeface.BOLD);
        topConnectionText.setLetterSpacing(0.12f);
        topStatusPill.addView(topStatusIcon,
                new LinearLayout.LayoutParams(dp(14), dp(14)));
        LinearLayout.LayoutParams statusTextParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        statusTextParams.setMargins(dp(4), 0, 0, 0);
        topStatusPill.addView(topConnectionText, statusTextParams);
        titles.addView(title);
        LinearLayout.LayoutParams pillParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        pillParams.topMargin = dp(2);
        titles.addView(topStatusPill, pillParams);
        bar.addView(titles, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageView settings = icon(R.drawable.ic_settings, TEXT_MUTED, 22);
        settings.setPadding(dp(11), dp(11), dp(11), dp(11));
        settings.setContentDescription("Settings");
        settings.setBackground(circleDrawable(WHITE));
        settings.setOnClickListener(v -> showPage(PAGE_SETTINGS));
        bar.addView(settings, new LinearLayout.LayoutParams(dp(44), dp(44)));
        return bar;
    }

    private View buildBottomNavigation() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(14), dp(7), dp(14), dp(9));
        nav.setBackground(roundRect(WHITE, 0, OUTLINE, 1));
        nav.setElevation(dp(10));

        String[] labels = {"Device", "Control", "Presets", "Help", "Settings"};
        int[] icons = {
                R.drawable.ic_device,
                R.drawable.ic_tune,
                R.drawable.ic_presets,
                R.drawable.ic_help,
                R.drawable.ic_settings
        };
        for (int i = 0; i < labels.length; i++) {
            final int page = i;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(dp(5), dp(4), dp(5), dp(4));
            item.setContentDescription(labels[i] + " tab");
            item.setBackground(roundRect(WHITE, 16,
                    Color.TRANSPARENT, 0));

            ImageView itemIcon = icon(icons[i], TEXT_MUTED, 21);
            item.addView(itemIcon, new LinearLayout.LayoutParams(
                    dp(24), dp(24)));
            TextView itemLabel = text(labels[i], 11, TEXT_MUTED, Typeface.BOLD);
            itemLabel.setGravity(Gravity.CENTER);
            item.addView(itemLabel);

            item.setOnClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
                showPage(page);
            });
            navItems[i] = item;
            navIcons[i] = itemIcon;
            navLabels[i] = itemLabel;

            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(0, dp(56), 1f);
            params.setMargins(dp(4), 0, dp(4), 0);
            nav.addView(item, params);
        }
        return nav;
    }

    private void showPage(int page) {
        currentPage = page;
        updateTopConnection();
        for (int i = 0; i < navItems.length; i++) {
            boolean active = i == page;
            navIcons[i].setImageTintList(ColorStateList.valueOf(
                    active ? PRIMARY : TEXT_MUTED));
            navLabels[i].setTextColor(active ? PRIMARY : TEXT_MUTED);
            navItems[i].setBackground(active
                    ? roundRect(PRIMARY_CONTAINER, 16, Color.TRANSPARENT, 0)
                    : roundRect(WHITE, 16,
                    Color.TRANSPARENT, 0));
            springScale(navItems[i], active ? 1f : 0.96f);
        }
        renderCurrentPage();
    }

    private void renderCurrentPage() {
        if (adminWorkspaceVisible) {
            showAdminWorkspace();
            return;
        }
        if (pageContainer == null) {
            return;
        }
        pageContainer.removeAllViews();
        View page;
        if (currentPage == PAGE_CONTROL) {
            page = buildControlPage();
        } else if (currentPage == PAGE_PRESETS) {
            page = buildPresetsPage();
        } else if (currentPage == PAGE_HELP) {
            page = buildHelpPage();
        } else if (currentPage == PAGE_SETTINGS) {
            page = buildSettingsPage();
        } else {
            page = buildDevicePage();
        }
        pageContainer.addView(page);
        springPageIn(page);
    }

    private View buildDevicePage() {
        LinearLayout content = pageColumn();

        content.addView(text("Device", 34, TEXT, Typeface.BOLD));
        TextView intro = text(
                connected ? "NeuroVibe is connected and ready."
                        : "Your private, local NeuroVibe connection.",
                14, TEXT_MUTED, Typeface.NORMAL);
        addWithTop(content, intro, 6);

        if (ptpEnabled) {
            LinearLayout assignment = new LinearLayout(this);
            assignment.setGravity(Gravity.CENTER_VERTICAL);
            assignment.setPadding(dp(15), dp(13), dp(15), dp(13));
            assignment.setBackground(roundRect(PRIMARY_CONTAINER, 18,
                    PRIMARY, 1));

            TextView avatar = text(ptpPatientName.isEmpty() ? "?" :
                            ptpPatientName.substring(0, 1).toUpperCase(Locale.US),
                    18, WHITE, Typeface.BOLD);
            avatar.setGravity(Gravity.CENTER);
            avatar.setBackground(circleDrawable(PRIMARY));
            assignment.addView(avatar,
                    new LinearLayout.LayoutParams(dp(46), dp(46)));

            LinearLayout patientCopy = new LinearLayout(this);
            patientCopy.setOrientation(LinearLayout.VERTICAL);
            patientCopy.addView(text("ASSIGNED PATIENT", 10,
                    PRIMARY, Typeface.BOLD));
            TextView patientName = text(ptpPatientName, 17,
                    TEXT, Typeface.BOLD);
            addWithTop(patientCopy, patientName, 3);
            LinearLayout.LayoutParams patientCopyParams =
                    new LinearLayout.LayoutParams(
                            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            patientCopyParams.setMargins(dp(12), 0, dp(8), 0);
            assignment.addView(patientCopy, patientCopyParams);
            assignment.addView(statusPill("PTP active", SUCCESS,
                    Color.rgb(235, 249, 239)));
            addWithTop(content, assignment, 14);
        }

        LinearLayout hero = card();
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        if (connected) {
            hero.setBackground(roundRect(WHITE, 24, LAVENDER, 2));
            hero.setElevation(dp(8));
        }

        ImageView product = new ImageView(this);
        product.setImageResource(R.drawable.hero_device_close);
        product.setScaleType(ImageView.ScaleType.CENTER_CROP);
        product.setContentDescription(
                "NeuroVibe device");
        product.setBackground(roundRect(SURFACE_HIGH, 20,
                Color.TRANSPARENT, 0));
        product.setClipToOutline(true);
        hero.addView(product, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(250)));

        TextView deviceName = text("NeuroVibe", 24, TEXT, Typeface.BOLD);
        deviceName.setGravity(Gravity.CENTER);
        addWithTop(hero, deviceName, 14);

        String status = connected ? "Connected · Ready"
                : connecting ? "Connecting…"
                : scanning ? "Searching nearby…"
                : communicationError ? "Communication error"
                : "Not connected";
        int statusColor = connected ? SUCCESS
                : communicationError ? ERROR
                : (scanning || connecting) ? PRIMARY : TEXT_MUTED;
        TextView state = text(status, 13, statusColor, Typeface.BOLD);
        state.setGravity(Gravity.CENTER);
        state.setPadding(dp(12), dp(6), dp(12), dp(6));
        state.setBackground(roundRect(
                connected ? Color.rgb(234, 249, 245)
                        : communicationError ? ERROR_LIGHT : SURFACE_LOW,
                99, statusColor, 1));
        LinearLayout.LayoutParams stateParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        stateParams.gravity = Gravity.CENTER_HORIZONTAL;
        stateParams.topMargin = dp(7);
        hero.addView(state, stateParams);

        Button connect = connected
                ? outlineButton("Disconnect device", ERROR)
                : primaryButton(scanning ? "Searching…" :
                        connecting ? "Connecting…" : "Connect NeuroVibe");
        setButtonIcon(connect, R.drawable.ic_signal,
                connected ? ERROR : WHITE);
        connect.setEnabled(!scanning && !connecting);
        connect.setOnClickListener(v -> {
            if (connected || bluetoothGatt != null) {
                disconnectDevice();
            } else {
                ensurePermissionsAndScan();
            }
        });
        addWithTop(hero, connect, 24);
        addWithTop(content, hero, 20);

        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout signal = smallInfoCard(R.drawable.ic_signal, "Connection quality",
                connected ? "Ready" : "N/A");
        LinearLayout protocol = smallInfoCard(R.drawable.ic_signal,
                "Connection", "Private local link");
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        half.setMargins(0, 0, dp(6), 0);
        stats.addView(signal, half);
        LinearLayout.LayoutParams halfRight = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        halfRight.setMargins(dp(6), 0, 0, 0);
        stats.addView(protocol, halfRight);
        addWithTop(content, stats, 12);

        if (connected) {
            LinearLayout connectionInfo = card();
            connectionInfo.setPadding(0, 0, 0, 0);
            connectionInfo.addView(insetActionRow(R.drawable.ic_signal,
                    "Connection quality",
                    lastRssi == 0 ? "Reading unavailable" : "Available",
                    lastRssi <= -70 ? "Fair" : "Strong"));
            connectionInfo.addView(hairline());
            connectionInfo.addView(insetActionRow(R.drawable.ic_stop,
                    "Disconnect protection", "Device stops safely", "Armed"));
            addWithTop(content, connectionInfo, 12);

            Button control = primaryButton("Go to Control");
            setButtonIcon(control, R.drawable.ic_tune, WHITE);
            control.setOnClickListener(v -> showPage(PAGE_CONTROL));
            addWithTop(content, control, 16);
        }

        if (deviceNotFound || communicationError) {
            LinearLayout troubleshooting = tintedCard();
            troubleshooting.setOrientation(LinearLayout.HORIZONTAL);
            ImageView helpIcon = icon(communicationError
                            ? R.drawable.ic_stop : R.drawable.ic_help,
                    WHITE, 20);
            helpIcon.setPadding(dp(10), dp(10), dp(10), dp(10));
            helpIcon.setBackground(roundRect(
                    communicationError ? ERROR : PRIMARY,
                    10, Color.TRANSPARENT, 0));
            troubleshooting.addView(helpIcon,
                    new LinearLayout.LayoutParams(dp(42), dp(42)));
            LinearLayout helpCopy = new LinearLayout(this);
            helpCopy.setOrientation(LinearLayout.VERTICAL);
            helpCopy.addView(text(
                    communicationError ? "Communication interrupted"
                            : "Device not found?",
                    18, communicationError ? ERROR : PRIMARY, Typeface.BOLD));
            TextView helpBody = text(communicationError
                            ? "NeuroVibe was stopped. Reconnect before continuing."
                            : "Ensure nearby device access is on and NeuroVibe is nearby, powered, and ready.",
                    14, TEXT_MUTED, Typeface.NORMAL);
            helpBody.setPadding(0, dp(4), 0, 0);
            helpCopy.addView(helpBody);
            LinearLayout.LayoutParams helpParams =
                    new LinearLayout.LayoutParams(
                            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            helpParams.setMargins(dp(14), 0, 0, 0);
            troubleshooting.addView(helpCopy, helpParams);
            addWithTop(content, troubleshooting, 16);
        }

        TextView note = text(
                "No system pairing needed · Connection stays local to this phone",
                13, TEXT_MUTED, Typeface.NORMAL);
        note.setGravity(Gravity.CENTER);
        note.setPadding(dp(10), dp(18), dp(10), dp(8));
        content.addView(note);
        return scroll(content);
    }

    private View buildControlPage() {
        LinearLayout content = pageColumn();

        content.addView(text("Control", 34, TEXT, Typeface.BOLD));
        TextView subtitle = text(ptpEnabled
                        ? ptpPatientName + " · Administrator-approved levels only."
                        : "Fine-tune your NeuroVibe level in real time.",
                14, TEXT_MUTED, Typeface.NORMAL);
        addWithTop(content, subtitle, 5);

        if (ptpEnabled) {
            LinearLayout patientMode = new LinearLayout(this);
            patientMode.setGravity(Gravity.CENTER_VERTICAL);
            patientMode.setPadding(dp(14), dp(11), dp(14), dp(11));
            patientMode.setBackground(roundRect(PRIMARY_CONTAINER, 16,
                    PRIMARY, 1));
            patientMode.addView(icon(R.drawable.ic_brain_circuit, PRIMARY, 20),
                    new LinearLayout.LayoutParams(dp(30), dp(30)));
            LinearLayout patientModeCopy = new LinearLayout(this);
            patientModeCopy.setOrientation(LinearLayout.VERTICAL);
            patientModeCopy.addView(text("PTP · Patient mode", 13,
                    PRIMARY, Typeface.BOLD));
            addWithTop(patientModeCopy, text(
                    "Low, Medium, and High are locked by the administrator.",
                    11, TEXT_MUTED, Typeface.NORMAL), 2);
            LinearLayout.LayoutParams patientModeCopyParams =
                    new LinearLayout.LayoutParams(
                            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            patientModeCopyParams.setMargins(dp(9), 0, 0, 0);
            patientMode.addView(patientModeCopy, patientModeCopyParams);
            addWithTop(content, patientMode, 14);
        }

        LinearLayout console = card();
        console.setPadding(dp(18), dp(16), dp(18), dp(18));
        console.setBackground(roundRect(WHITE, 26,
                connected ? Color.rgb(220, 215, 249) : OUTLINE, 1));

        LinearLayout consoleHeader = new LinearLayout(this);
        consoleHeader.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout consoleTitle = new LinearLayout(this);
        consoleTitle.setOrientation(LinearLayout.VERTICAL);
        TextView liveLabel = sectionEyebrow(connected ? "LIVE CONTROL" : "CONTROL LOCKED");
        consoleTitle.addView(liveLabel);
        TextView motorCopy = text("NeuroVibe control", 18, TEXT, Typeface.BOLD);
        addWithTop(consoleTitle, motorCopy, 3);
        consoleHeader.addView(consoleTitle, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout statePill = statusPill(connected && currentMotorValue > 0
                        ? "Active" : connected ? "Ready" : "Offline",
                connected ? SUCCESS : TEXT_MUTED,
                connected ? Color.rgb(235, 249, 239) : SURFACE_LOW);
        consoleHeader.addView(statePill);
        console.addView(consoleHeader);

        motorDial = new CircularDialView(this);
        motorDial.setValue(currentMotorValue);
        motorDial.setEnabled(connected && !ptpEnabled);
        motorDial.setAlpha(ptpEnabled ? 0.55f : 1f);
        motorDial.setListener(new CircularDialView.Listener() {
            @Override
            public void onValueChanged(int value, boolean fromUser) {
                currentMotorValue = value;
                updateControlReadout(value);
            }

            @Override
            public void onValueCommitted(int value) {
                commitMotorValue(value);
            }
        });
        LinearLayout.LayoutParams dialParams = new LinearLayout.LayoutParams(
                dp(286), dp(286));
        dialParams.gravity = Gravity.CENTER_HORIZONTAL;
        dialParams.topMargin = dp(8);
        console.addView(motorDial, dialParams);

        LinearLayout fineTune = new LinearLayout(this);
        fineTune.setGravity(Gravity.CENTER);
        Button decrease = roundIconButton("−",
                connected && !ptpEnabled && currentMotorValue > 0);
        decrease.setContentDescription("Decrease frequency by 10 hertz");
        decrease.setOnClickListener(v -> adjustMotorValue(-10));
        fineTune.addView(decrease, new LinearLayout.LayoutParams(dp(52), dp(52)));
        LinearLayout fineCopy = new LinearLayout(this);
        fineCopy.setOrientation(LinearLayout.VERTICAL);
        fineCopy.setGravity(Gravity.CENTER);
        TextView fineLabel = text(ptpEnabled ? "ADMIN LOCKED" : "10 Hz STEPS",
                11, TEXT_MUTED, Typeface.BOLD);
        fineLabel.setLetterSpacing(0.1f);
        fineLabel.setGravity(Gravity.CENTER);
        fineCopy.addView(fineLabel);
        TextView dragHint = text(ptpEnabled
                        ? "Use a quick level below" : "Drag the ring for precision", 11,
                MUTED, Typeface.NORMAL);
        dragHint.setGravity(Gravity.CENTER);
        addWithTop(fineCopy, dragHint, 2);
        LinearLayout.LayoutParams fineLabelParams = new LinearLayout.LayoutParams(
                dp(166), ViewGroup.LayoutParams.WRAP_CONTENT);
        fineTune.addView(fineCopy, fineLabelParams);
        Button increase = roundIconButton("+",
                connected && !ptpEnabled && currentMotorValue < MAX_VALUE);
        increase.setContentDescription("Increase frequency by 10 hertz");
        increase.setOnClickListener(v -> adjustMotorValue(10));
        fineTune.addView(increase, new LinearLayout.LayoutParams(dp(52), dp(52)));
        addWithTop(console, fineTune, 4);
        addWithTop(content, console, 18);

        addWithTop(content, sectionEyebrow("QUICK LEVELS"), 20);
        LinearLayout quickLevels = new LinearLayout(this);
        quickLevels.setGravity(Gravity.CENTER);
        int[] quickValues = ptpEnabled
                ? new int[]{ptpLowFrequency, ptpMediumFrequency, ptpHighFrequency}
                : new int[]{58, 115, 173};
        String[] quickLabels = ptpEnabled
                ? new String[]{"Low\n" + ptpLowFrequency + " Hz",
                "Medium\n" + ptpMediumFrequency + " Hz",
                "High\n" + ptpHighFrequency + " Hz"}
                : new String[]{"Gentle\n25%", "Balanced\n50%", "Strong\n75%"};
        for (int index = 0; index < quickValues.length; index++) {
            final int quickValue = quickValues[index];
            Button quick = quickLevelButton(quickLabels[index], quickValue,
                    currentMotorValue == quickValue);
            quick.setEnabled(connected);
            quick.setAlpha(connected ? 1f : 0.42f);
            quick.setOnClickListener(v -> setMotorValueFromShortcut(quickValue));
            LinearLayout.LayoutParams quickParams = new LinearLayout.LayoutParams(
                    0, dp(64), 1f);
            if (index > 0) {
                quickParams.setMargins(dp(8), 0, 0, 0);
            }
            quickLevels.addView(quick, quickParams);
        }
        addWithTop(content, quickLevels, 10);

        if (!ptpEnabled) {
            LinearLayout presetRow = insetActionRow(R.drawable.ic_presets,
                    "Preset library",
                    selectedPresetName == null ? "Choose a starting point"
                            : selectedPresetName + " · " + selectedPresetValue + " Hz",
                    "Browse");
            presetRow.setBackground(rippleBackground(WHITE, 20));
            presetRow.setElevation(dp(1));
            presetRow.setOnClickListener(v -> showPage(PAGE_PRESETS));
            addWithTop(content, presetRow, 20);
        }

        LinearLayout metrics = new LinearLayout(this);
        LinearLayout bothMotors = metricCard("FREQUENCY",
                currentMotorValue + " Hz", "Device level");
        LinearLayout range = metricCard("INTENSITY",
                Math.round(currentMotorValue * 100f / MAX_VALUE) + "%",
                "Selected level " + currentMotorValue);
        LinearLayout.LayoutParams metricLeft = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        metricLeft.setMargins(0, 0, dp(6), 0);
        metrics.addView(bothMotors, metricLeft);
        LinearLayout.LayoutParams metricRight = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        metricRight.setMargins(dp(6), 0, 0, 0);
        metrics.addView(range, metricRight);
        addWithTop(content, metrics, 12);

        if (!connected) {
            Button connect = secondaryButton("Connect NeuroVibe");
            setButtonIcon(connect, R.drawable.ic_signal, PRIMARY);
            connect.setOnClickListener(v -> showPage(PAGE_DEVICE));
            addWithTop(content, connect, 18);
        }

        Button stop = primaryButton("Stop session");
        stop.setBackground(roundRect(ERROR, 18, Color.TRANSPARENT, 0));
        setButtonIcon(stop, R.drawable.ic_stop, WHITE);
        stop.setEnabled(connected && currentMotorValue > 0);
        stop.setAlpha(stop.isEnabled() ? 1f : 0.38f);
        stop.setOnClickListener(v -> emergencyStop());
        addWithTop(content, stop, 18);

        TextView guidance = text(ptpEnabled
                        ? "Only the three administrator-approved quick levels can start a session. Stop immediately if discomfort occurs."
                        : "The level updates after you release the dial. Begin gently and stop immediately if discomfort occurs.",
                12, TEXT_MUTED, Typeface.NORMAL);
        guidance.setGravity(Gravity.CENTER);
        guidance.setPadding(dp(16), dp(14), dp(16), dp(4));
        content.addView(guidance);
        return scroll(content);
    }

    private Button quickLevelButton(String label, int value, boolean selected) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(13);
        button.setTextColor(selected ? WHITE : TEXT);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(6), 0, dp(6), 0);
        button.setBackground(rippleBackground(selected ? PRIMARY : WHITE, 16));
        button.setElevation(selected ? dp(3) : dp(1));
        button.setContentDescription(label.replace("\n", " ") +
                ", set level to " + value + " hertz");
        return button;
    }

    private void setMotorValueFromShortcut(int value) {
        if (!connected) {
            return;
        }
        currentMotorValue = Math.max(0, Math.min(MAX_VALUE, value));
        updateControlReadout(currentMotorValue);
        commitMotorValue(currentMotorValue);
        renderCurrentPage();
    }

    private void updateControlReadout(int value) {
        if (frequencyText != null) {
            frequencyText.setText(value + " Hz");
        }
        if (percentText != null) {
            percentText.setText(
                    Math.round(value * 100f / MAX_VALUE) + "% level");
        }
        if (activeStateText != null) {
            activeStateText.setText(value > 0
                    ? "Session active · NeuroVibe"
                    : "Ready · NeuroVibe");
        }
        if (pulseRing != null) {
            pulseRing.setState(connected, value);
        }
        if (motorDial != null && motorDial.getValue() != value) {
            motorDial.setValue(value);
        }
    }

    private void adjustMotorValue(int delta) {
        if (!connected || ptpEnabled) {
            return;
        }
        int next = Math.max(0, Math.min(MAX_VALUE, currentMotorValue + delta));
        currentMotorValue = next;
        if (motorSlider != null) {
            motorSlider.setValue(next);
        }
        updateControlReadout(next);
        commitMotorValue(next);
        if (motorDial != null) {
            motorDial.setScaleX(0.975f);
            motorDial.setScaleY(0.975f);
            springScale(motorDial, 1f);
        }
    }

    private View buildPresetsPage() {
        LinearLayout content = pageColumn();
        if (ptpEnabled) {
            content.addView(text("Patient presets", 34, TEXT, Typeface.BOLD));
            addWithTop(content, text(
                    ptpPatientName + " can use only the three levels approved by the administrator.",
                    14, TEXT_MUTED, Typeface.NORMAL), 6);
            LinearLayout locked = card();
            locked.setBackground(roundRect(SURFACE_HIGH, 22, PRIMARY, 1));
            locked.addView(text("PTP mode is active", 20, TEXT, Typeface.BOLD));
            addWithTop(locked, text(
                    "Custom and built-in presets are unavailable until an administrator disables patient mode.",
                    12, TEXT_MUTED, Typeface.NORMAL), 4);
            String[] names = {"Low", "Medium", "High"};
            int[] values = {ptpLowFrequency, ptpMediumFrequency,
                    ptpHighFrequency};
            for (int index = 0; index < names.length; index++) {
                LinearLayout row = insetActionRow(R.drawable.ic_presets,
                        names[index] + " level", values[index] + " Hz",
                        "Approved");
                row.setBackground(roundRect(PRIMARY_CONTAINER, 15,
                        Color.TRANSPARENT, 0));
                addWithTop(locked, row, index == 0 ? 16 : 8);
            }
            addWithTop(content, locked, 18);
            Button control = primaryButton("Open approved controls");
            setButtonIcon(control, R.drawable.ic_tune, WHITE);
            control.setOnClickListener(v -> showPage(PAGE_CONTROL));
            addWithTop(content, control, 16);
            return scroll(content);
        }
        content.addView(text("Presets", 34, TEXT, Typeface.BOLD));
        TextView intro = text(
                "Choose a feel, preview its level, then start when you are ready.",
                14, TEXT_MUTED, Typeface.NORMAL);
        addWithTop(content, intro, 6);

        LinearLayout segments = new LinearLayout(this);
        segments.setPadding(dp(3), dp(3), dp(3), dp(3));
        segments.setBackground(roundRect(Color.rgb(232, 231, 239), 10,
                Color.TRANSPARENT, 0));
        Button builtIn = segmentButton("Built-in", !showCustomPresets);
        builtIn.setOnClickListener(v -> {
            showCustomPresets = false;
            renderCurrentPage();
        });
        segments.addView(builtIn, new LinearLayout.LayoutParams(
                0, dp(38), 1f));
        Button customTab = segmentButton("Custom", showCustomPresets);
        customTab.setOnClickListener(v -> {
            showCustomPresets = true;
            renderCurrentPage();
        });
        segments.addView(customTab, new LinearLayout.LayoutParams(
                0, dp(38), 1f));
        addWithTop(content, segments, 18);

        LinearLayout libraryHeader = new LinearLayout(this);
        libraryHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView collection = sectionEyebrow(showCustomPresets
                ? "YOUR SAVED LEVEL" : "CURATED LEVELS");
        libraryHeader.addView(collection, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView count = text(showCustomPresets ? "1 PRESET" : "3 PRESETS",
                10, MUTED, Typeface.BOLD);
        count.setLetterSpacing(0.1f);
        libraryHeader.addView(count);
        addWithTop(content, libraryHeader, 22);

        if (!showCustomPresets) {
            addWithTop(content, presetCard(R.drawable.ic_presets, "Gentle",
                    "A soft introduction for short sessions", 60), 10);
            addWithTop(content, presetCard(R.drawable.ic_tune, "Balanced",
                    "An even, moderate everyday level", 115), 10);
            addWithTop(content, presetCard(R.drawable.ic_signal, "Strong",
                    "A firm and clearly pronounced level", 180), 10);
        } else {

            int custom = preferences.getInt("custom_preset", 120);
            LinearLayout customCard = card();
            if (selectedPresetValue == custom &&
                    "Custom".equals(selectedPresetName)) {
                customCard.setBackground(roundRect(
                        SURFACE_HIGH, 22, PRIMARY, 2));
            }
            LinearLayout customRow = new LinearLayout(this);
            customRow.setGravity(Gravity.CENTER_VERTICAL);
            ImageView customIcon = icon(R.drawable.ic_presets, PRIMARY, 22);
            customIcon.setPadding(dp(10), dp(10), dp(10), dp(10));
            customIcon.setBackground(circleDrawable(PRIMARY_CONTAINER));
            customRow.addView(customIcon,
                    new LinearLayout.LayoutParams(dp(46), dp(46)));
            LinearLayout customText = new LinearLayout(this);
            customText.setOrientation(LinearLayout.VERTICAL);
            customText.addView(text("My preset", 18, TEXT, Typeface.BOLD));
            customText.addView(text("Saved locally · " + custom + " Hz · " +
                            Math.round(custom * 100f / MAX_VALUE) + "%",
                    12, TEXT_MUTED, Typeface.NORMAL));
            LinearLayout.LayoutParams customTextParams =
                    new LinearLayout.LayoutParams(
                            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            customTextParams.setMargins(dp(12), 0, dp(8), 0);
            customRow.addView(customText, customTextParams);
            Button edit = smallOutlineButton("Edit");
            edit.setOnClickListener(v -> showCustomPresetEditor());
            customRow.addView(edit);
            customCard.addView(customRow);
            customCard.setContentDescription(
                    "Custom preset, " + custom + " hertz. Tap to preview.");
            customCard.setOnClickListener(v -> selectPreset("Custom", custom));
            addWithTop(content, customCard, 16);
        }

        if (selectedPresetValue >= 0) {
            LinearLayout preview = card();
            preview.setPadding(dp(20), dp(19), dp(20), dp(20));
            preview.setBackground(roundRect(SURFACE_HIGH, 24, LAVENDER, 2));
            LinearLayout previewTop = new LinearLayout(this);
            previewTop.setGravity(Gravity.CENTER_VERTICAL);
            TextView previewLabel = text("READY TO APPLY",
                    11, PRIMARY, Typeface.BOLD);
            previewLabel.setLetterSpacing(0.12f);
            previewTop.addView(previewLabel, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            previewTop.addView(statusPill(connected ? "Device ready" : "Connect first",
                    connected ? SUCCESS : TEXT_MUTED,
                    connected ? Color.rgb(235, 249, 239) : SURFACE_LOW));
            preview.addView(previewTop);
            TextView previewValue = text(
                    selectedPresetName + " · " + selectedPresetValue + " Hz",
                    24, TEXT, Typeface.BOLD);
            addWithTop(preview, previewValue, 12);
            preview.addView(text(
                    Math.round(selectedPresetValue * 100f / MAX_VALUE) +
                            "% level · NeuroVibe",
                    14, TEXT_MUTED, Typeface.NORMAL));
            Button start = primaryButton("Start " + selectedPresetName);
            setButtonIcon(start, R.drawable.ic_play, WHITE);
            start.setEnabled(connected);
            start.setAlpha(connected ? 1f : 0.45f);
            start.setContentDescription("Start " + selectedPresetName +
                    " preset at " + selectedPresetValue + " hertz");
            start.setOnClickListener(v -> startSelectedPreset());
            addWithTop(preview, start, 16);
            addWithTop(content, preview, 18);
        }

        LinearLayout safeNote = tintedCard();
        safeNote.setOrientation(LinearLayout.HORIZONTAL);
        safeNote.setGravity(Gravity.CENTER_VERTICAL);
        safeNote.addView(icon(R.drawable.ic_info, PRIMARY, 18),
                new LinearLayout.LayoutParams(dp(26), dp(26)));
        TextView safeCopy = text(
                "Preview is selection only. The session begins only after you press Start.",
                12, TEXT_MUTED, Typeface.NORMAL);
        LinearLayout.LayoutParams safeCopyParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        safeCopyParams.setMargins(dp(10), 0, 0, 0);
        safeNote.addView(safeCopy, safeCopyParams);
        addWithTop(content, safeNote, 12);

        if (connected && currentMotorValue > 0) {
            Button stop = primaryButton("EMERGENCY STOP");
            stop.setBackground(roundRect(ERROR, 18, Color.TRANSPARENT, 0));
            setButtonIcon(stop, R.drawable.ic_stop, WHITE);
            stop.setOnClickListener(v -> emergencyStop());
            addWithTop(content, stop, 18);
        }
        return scroll(content);
    }

    private LinearLayout presetCard(
            int iconResource, String name, String description, int value) {
        LinearLayout card = card();
        card.setPadding(dp(17), dp(16), dp(17), dp(15));
        boolean selected = selectedPresetValue == value &&
                name.equals(selectedPresetName);
        if (selected) {
            card.setBackground(roundRect(SURFACE_HIGH, 24, PRIMARY, 2));
        }
        card.setContentDescription(name + " preset, " + value +
                " hertz, " + Math.round(value * 100f / MAX_VALUE) +
                " percent. Tap to preview.");
        card.setOnClickListener(v -> selectPreset(name, value));
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);

        ImageView iconView = icon(iconResource, selected ? WHITE : PRIMARY, 22);
        iconView.setPadding(dp(10), dp(10), dp(10), dp(10));
        iconView.setBackground(circleDrawable(selected ? PRIMARY : PRIMARY_CONTAINER));
        row.addView(iconView, new LinearLayout.LayoutParams(dp(46), dp(46)));

        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        names.addView(text(name, 19, TEXT, Typeface.BOLD));
        names.addView(text(description, 12, TEXT_MUTED, Typeface.NORMAL));
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        nameParams.setMargins(dp(12), 0, dp(8), 0);
        row.addView(names, nameParams);

        TextView level = text(Math.round(value * 100f / MAX_VALUE) + "%",
                13, selected ? WHITE : PRIMARY, Typeface.BOLD);
        level.setGravity(Gravity.CENTER);
        level.setBackground(roundRect(selected ? PRIMARY : PRIMARY_CONTAINER,
                99, Color.TRANSPARENT, 0));
        level.setPadding(dp(12), dp(7), dp(12), dp(7));
        row.addView(level);
        card.addView(row);

        LinearLayout valueRow = new LinearLayout(this);
        valueRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView output = text("SELECTED LEVEL", 10, MUTED, Typeface.BOLD);
        output.setLetterSpacing(0.1f);
        valueRow.addView(output, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        valueRow.addView(text(value + " Hz", 15, PRIMARY, Typeface.BOLD));
        addWithTop(card, valueRow, 15);

        PresetLevelBarView preview = new PresetLevelBarView(this);
        preview.setValue(value);
        LinearLayout.LayoutParams previewParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(38));
        previewParams.topMargin = dp(4);
        card.addView(preview, previewParams);

        TextView previewHint = text(selected ? "✓ Selected for preview"
                        : "Tap to preview this level",
                12, selected ? PRIMARY : MUTED, Typeface.BOLD);
        previewHint.setGravity(Gravity.START);
        addWithTop(card, previewHint, 5);
        return card;
    }

    private View buildHelpPage() {
        LinearLayout content = pageColumn();
        content.addView(text("Help & support", 34, TEXT, Typeface.BOLD));
        addWithTop(content, text("Guides for setup, connection, safety, and care.",
                14, TEXT_MUTED, Typeface.NORMAL), 6);

        LinearLayout status = statusPill("Private connection · Local only",
                SUCCESS, Color.rgb(235, 249, 239));
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = dp(16);
        content.addView(status, statusParams);

        addWithTop(content, sectionEyebrow("DEVICE GUIDES"), 24);
        LinearLayout guides = card();
        guides.setPadding(0, 0, 0, 0);
        LinearLayout hardware = insetActionRow(R.drawable.ic_device,
                "Device care guide", "Care, storage, and safe handling", "Open");
        hardware.setOnClickListener(v -> showHardwareHelp());
        guides.addView(hardware);
        guides.addView(hairline());
        LinearLayout bluetooth = insetActionRow(R.drawable.ic_signal,
                "Connection troubleshooting", "Access, range, and reconnecting", "Open");
        bluetooth.setOnClickListener(v -> showConnectivityHelp());
        guides.addView(bluetooth);
        guides.addView(hairline());
        LinearLayout safetyGuide = insetActionRow(R.drawable.ic_stop,
                "Safety & fail-safe", "Automatic zero and responsible use", "Read");
        safetyGuide.setOnClickListener(v -> showSafetyDetails());
        guides.addView(safetyGuide);
        guides.addView(hairline());
        LinearLayout replay = insetActionRow(R.drawable.ic_play,
                "Replay welcome flow", "Device, privacy, and safety", "Start");
        replay.setOnClickListener(v -> {
            onboardingStep = 0;
            showWelcomeScreen();
        });
        guides.addView(replay);
        addWithTop(content, guides, 10);

        addWithTop(content, sectionEyebrow("QUICK PAIRING"), 24);
        LinearLayout pairing = card();
        pairing.addView(numberedStep("1", "Power on NeuroVibe",
                "Confirm NeuroVibe is powered and ready."));
        pairing.addView(hairline());
        pairing.addView(numberedStep("2", "Keep it nearby",
                "Place NeuroVibe within arm's reach of this phone or tablet."));
        pairing.addView(hairline());
        pairing.addView(numberedStep("3", "Connect inside the app",
                "Use Connect on the Device tab. System pairing and PIN codes are not required."));
        addWithTop(content, pairing, 10);

        LinearLayout offline = tintedCard();
        offline.setOrientation(LinearLayout.HORIZONTAL);
        offline.setGravity(Gravity.CENTER_VERTICAL);
        offline.addView(icon(R.drawable.ic_brain_circuit, PRIMARY, 20),
                new LinearLayout.LayoutParams(dp(30), dp(30)));
        TextView offlineText = text(
                "NeuroSense operates offline through a private local connection.",
                13, TEXT_MUTED, Typeface.BOLD);
        LinearLayout.LayoutParams offlineParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        offlineParams.setMargins(dp(10), 0, 0, 0);
        offline.addView(offlineText, offlineParams);
        addWithTop(content, offline, 12);

        Button stop = primaryButton("STOP DEVICE");
        stop.setBackground(rippleBackground(ERROR, 16));
        setButtonIcon(stop, R.drawable.ic_stop, WHITE);
        stop.setEnabled(connected && currentMotorValue > 0);
        stop.setAlpha(stop.isEnabled() ? 1f : 0.38f);
        stop.setOnClickListener(v -> emergencyStop());
        addWithTop(content, stop, 18);

        TextView disclaimer = text(
                "NeuroSense is a wellness app and does not diagnose or treat a medical condition.",
                12, MUTED, Typeface.NORMAL);
        disclaimer.setGravity(Gravity.CENTER);
        disclaimer.setPadding(dp(12), dp(16), dp(12), dp(6));
        content.addView(disclaimer);
        return scroll(content);
    }

    private LinearLayout numberedStep(String number, String title, String detail) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.TOP);
        row.setPadding(0, dp(10), 0, dp(10));
        TextView index = text(number, 14, WHITE, Typeface.BOLD);
        index.setGravity(Gravity.CENTER);
        index.setBackground(circleDrawable(PRIMARY));
        row.addView(index, new LinearLayout.LayoutParams(dp(30), dp(30)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text(title, 16, TEXT, Typeface.BOLD));
        addWithTop(copy, text(detail, 13, TEXT_MUTED, Typeface.NORMAL), 3);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        copyParams.setMargins(dp(12), 0, 0, 0);
        row.addView(copy, copyParams);
        return row;
    }

    private LinearLayout numberedGuidance(
            String number, String title, String description) {
        LinearLayout row = card();
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView index = text(number, 20, PRIMARY_CONTAINER, Typeface.BOLD);
        row.addView(index, new LinearLayout.LayoutParams(dp(42),
                ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text(title, 16, TEXT, Typeface.BOLD));
        copy.addView(text(description, 13, TEXT_MUTED, Typeface.NORMAL));
        row.addView(copy, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    private TextView bullet(String message) {
        TextView view = text("•  " + message, 14, TEXT_MUTED, Typeface.NORMAL);
        view.setPadding(0, dp(10), 0, 0);
        return view;
    }

    private View buildSettingsPage() {
        LinearLayout content = pageColumn();
        content.addView(text("Settings", 34, TEXT, Typeface.BOLD));
        addWithTop(content, text(
                "NeuroSense is private, local, and designed for NeuroVibe.",
                14, TEXT_MUTED, Typeface.NORMAL), 6);

        addWithTop(content, sectionEyebrow("APP & DEVICE"), 26);
        LinearLayout appGroup = card();
        appGroup.setPadding(0, 0, 0, 0);
        LinearLayout about = insetActionRow(R.drawable.ic_info,
                "About NeuroSense", "Version " + getAppVersion(), "Details");
        about.setOnClickListener(v -> showSettings());
        appGroup.addView(about);
        appGroup.addView(hairline());
        LinearLayout connection = insetActionRow(R.drawable.ic_signal,
                "Device connection", connected ? "NeuroVibe connected" : "Not connected",
                connected ? "Ready" : "Open");
        connection.setOnClickListener(v -> showPage(PAGE_DEVICE));
        appGroup.addView(connection);
        addWithTop(content, appGroup, 10);

        addWithTop(content, sectionEyebrow("PRIVACY & ACCESS"), 24);
        LinearLayout privacyGroup = card();
        privacyGroup.setPadding(0, 0, 0, 0);
        LinearLayout privacy = insetActionRow(R.drawable.ic_brain_circuit,
                "Private by design", "No account · No cloud · No analytics", "Local");
        privacy.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Private by design")
                .setMessage("NeuroSense communicates directly with NeuroVibe through a private local connection. It does not require an account, internet connection, cloud service, analytics, or a remote database.")
                .setPositiveButton("Done", null)
                .show());
        privacyGroup.addView(privacy);
        privacyGroup.addView(hairline());
        LinearLayout permissions = insetActionRow(R.drawable.ic_settings,
                "Nearby Devices access", "Used only to find and connect", "Review");
        permissions.setOnClickListener(v -> showBluetoothPermissionRationale(
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                        ? new String[]{Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT}
                        : new String[]{Manifest.permission.ACCESS_FINE_LOCATION}));
        privacyGroup.addView(permissions);
        addWithTop(content, privacyGroup, 10);

        addWithTop(content, sectionEyebrow("ADMIN ACCESS"), 24);
        LinearLayout adminGroup = card();
        adminGroup.setPadding(0, 0, 0, 0);
        LinearLayout admin = insetActionRow(R.drawable.ic_settings,
                "Admin workspace",
                "Patients, sessions, frequency history, and Excel export",
                "Unlock");
        admin.setContentDescription(
                "Open password protected admin workspace");
        admin.setOnClickListener(v -> showAdminPasswordDialog());
        adminGroup.addView(admin);
        addWithTop(content, adminGroup, 10);

        addWithTop(content, sectionEyebrow("SAFETY & STATUS"), 24);
        LinearLayout guidanceGroup = card();
        guidanceGroup.setPadding(0, 0, 0, 0);
        LinearLayout replay = insetActionRow(R.drawable.ic_play,
                "Replay introduction", "Device, privacy, and safety", "Start");
        replay.setOnClickListener(v -> {
            onboardingStep = 0;
            showWelcomeScreen();
        });
        guidanceGroup.addView(replay);
        guidanceGroup.addView(hairline());
        LinearLayout safety = insetActionRow(R.drawable.ic_stop,
                "Safety information", "Automatic stop and responsible use", "Read");
        safety.setOnClickListener(v -> showSafetyDetails());
        guidanceGroup.addView(safety);
        guidanceGroup.addView(hairline());
        LinearLayout diagnostics = insetActionRow(R.drawable.ic_signal,
                "App status", "Private local summary", "View");
        diagnostics.setOnClickListener(v -> showDiagnostics());
        guidanceGroup.addView(diagnostics);
        addWithTop(content, guidanceGroup, 10);

        TextView footer = text(
                "NeuroSense is a wellness app and is not intended to diagnose or treat a medical condition.",
                12, MUTED, Typeface.NORMAL);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(dp(16), dp(24), dp(16), dp(12));
        content.addView(footer);
        return scroll(content);
    }

    private void showAdminPasswordDialog() {
        EditText password = adminField("Admin password",
                android.text.InputType.TYPE_CLASS_TEXT |
                        android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD,
                false);
        password.setContentDescription("Admin password");
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Admin access")
                .setMessage("Enter the local administrator password to open patient and session records.")
                .setView(password)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Unlock", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(
                AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (!ADMIN_PASSWORD.equals(password.getText().toString())) {
                password.setError("Incorrect password");
                password.selectAll();
                return;
            }
            dialog.dismiss();
            adminUnlocked = true;
            if (selectedAdminPatientId == null &&
                    !adminDataStore.patients().isEmpty()) {
                selectedAdminPatientId = adminDataStore.patients().get(0).id;
            }
            showAdminWorkspace();
        }));
        dialog.getWindow();
        dialog.show();
        password.requestFocus();
    }

    private void showAdminWorkspace() {
        if (!adminUnlocked) {
            showAdminPasswordDialog();
            return;
        }
        adminWorkspaceVisible = true;
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(WHITE);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                        View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        handler.removeCallbacks(adminDurationTick);
        adminDurationText = null;
        if (activeAdminSession != null) {
            showActiveAdminSession();
            return;
        }
        if (adminSessionSetupActive) {
            showAdminSessionSetup();
            return;
        }
        if (adminPage == 1) {
            showAdminHistoryPage();
            return;
        }
        if (adminPage == 2) {
            showAdminExportPage();
            return;
        }
        if (adminPage == 3) {
            showAdminPtpPage();
            return;
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.addView(adminTopBar("Admin workspace", "Local clinical records", false),
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(86)));
        root.addView(adminPageTabs(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(62)));

        LinearLayout content = pageColumn();
        LinearLayout hero = adminHeroCard();
        content.addView(hero);

        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.addView(adminStatCard(String.valueOf(adminDataStore.patients().size()),
                        "PATIENTS"),
                adminThirdParams(0, 6));
        stats.addView(adminStatCard(String.valueOf(adminDataStore.totalSessions()),
                        "SESSIONS"),
                adminThirdParams(6, 6));
        stats.addView(adminStatCard(String.valueOf(adminRecentSessionCount()),
                        "LAST 7 DAYS"),
                adminThirdParams(6, 0));
        addWithTop(content, stats, 14);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button addPatient = primaryButton("Add patient");
        setButtonIcon(addPatient, R.drawable.ic_brain_circuit, WHITE);
        addPatient.setOnClickListener(v -> showEnrollPatientDialog());
        attachPressAnimation(addPatient);
        actions.addView(addPatient, adminHalfParams(0, 5));
        Button viewHistory = secondaryButton("View history");
        setButtonIcon(viewHistory, R.drawable.ic_info, PRIMARY);
        viewHistory.setOnClickListener(v -> {
            adminPage = 1;
            showAdminWorkspace();
        });
        attachPressAnimation(viewHistory);
        actions.addView(viewHistory, adminHalfParams(5, 0));
        addWithTop(content, actions, 18);

        AdminDataStore.PatientRecord selected =
                adminDataStore.findPatient(selectedAdminPatientId);
        if (selected != null) {
            addWithTop(content, sectionEyebrow("CURRENT PATIENT"), 26);
            addWithTop(content, adminSelectedPatientCard(selected), 10);
        }

        AdminHistoryItem recent = adminMostRecentSession();
        if (recent != null) {
            addWithTop(content, sectionEyebrow("LATEST ACTIVITY"), 26);
            addWithTop(content, adminRecentActivityCard(recent), 10);
        }

        addWithTop(content, sectionEyebrow("PATIENT DIRECTORY"), 26);
        if (adminDataStore.patients().isEmpty()) {
            addWithTop(content, adminEmptyState(
                    "Your patient workspace is ready",
                    "Add the first patient to prepare sessions, capture frequency changes, and export a complete history."), 10);
        } else {
            for (AdminDataStore.PatientRecord patient : adminDataStore.patients()) {
                addWithTop(content, adminPatientCard(patient), 10);
            }
        }

        TextView footer = text(
                "Administrator access is local to this app. Lock the workspace when finished.",
                11, MUTED, Typeface.NORMAL);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(dp(12), dp(24), dp(12), dp(8));
        content.addView(footer);

        ScrollView scroll = scroll(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        springPageIn(scroll);
    }

    private LinearLayout adminHeroCard() {
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(20), dp(20), dp(20), dp(18));
        hero.setBackground(roundRect(PRIMARY, 24, Color.TRANSPARENT, 0));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text("Clinical workspace", 27, WHITE, Typeface.BOLD));
        addWithTop(copy, text("Everything needed for a focused patient session.",
                13, Color.rgb(235, 232, 255), Typeface.NORMAL), 5);
        top.addView(copy, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView device = text(connected ? "●  DEVICE READY" : "○  DEVICE OFFLINE",
                10, WHITE, Typeface.BOLD);
        device.setLetterSpacing(0.07f);
        device.setGravity(Gravity.CENTER);
        device.setPadding(dp(10), dp(7), dp(10), dp(7));
        device.setBackground(roundRect(connected
                        ? Color.rgb(73, 154, 94) : Color.rgb(82, 68, 176),
                99, Color.argb(90, 255, 255, 255), 1));
        top.addView(device);
        hero.addView(top);

        LinearLayout privacy = new LinearLayout(this);
        privacy.setGravity(Gravity.CENTER_VERTICAL);
        privacy.setPadding(dp(12), dp(10), dp(12), dp(10));
        privacy.setBackground(roundRect(Color.rgb(84, 68, 190), 14,
                Color.TRANSPARENT, 0));
        privacy.addView(icon(R.drawable.ic_brain_circuit, WHITE, 18),
                new LinearLayout.LayoutParams(dp(24), dp(24)));
        TextView privacyCopy = text(
                "Private by design · Records remain on this device",
                11, Color.rgb(241, 239, 255), Typeface.BOLD);
        LinearLayout.LayoutParams privacyParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        privacyParams.setMargins(dp(9), 0, 0, 0);
        privacy.addView(privacyCopy, privacyParams);
        addWithTop(hero, privacy, 17);
        return hero;
    }

    private LinearLayout adminSelectedPatientCard(
            AdminDataStore.PatientRecord patient) {
        LinearLayout detail = card();
        detail.setBackground(roundRect(WHITE, 22, PRIMARY, 1));

        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(adminPatientAvatar(patient, true),
                new LinearLayout.LayoutParams(dp(52), dp(52)));
        LinearLayout patientCopy = new LinearLayout(this);
        patientCopy.setOrientation(LinearLayout.VERTICAL);
        patientCopy.addView(text(patient.name, 22, TEXT, Typeface.BOLD));
        addWithTop(patientCopy, text(patient.phoneNumber, 13,
                TEXT_MUTED, Typeface.NORMAL), 2);
        LinearLayout.LayoutParams patientCopyParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        patientCopyParams.setMargins(dp(13), 0, dp(8), 0);
        heading.addView(patientCopy, patientCopyParams);
        heading.addView(statusPill(patient.sessions.size() +
                        (patient.sessions.size() == 1 ? " session" : " sessions"),
                PRIMARY, PRIMARY_CONTAINER));
        detail.addView(heading);

        String demographics = (patient.age > 0 ? patient.age + " years" :
                "Age not recorded") + "  ·  " +
                (patient.sex == null || patient.sex.isEmpty()
                        ? "Sex not recorded" : patient.sex);
        addWithTop(detail, text(demographics, 12,
                TEXT_MUTED, Typeface.BOLD), 12);
        if (patient.aadhaarId != null && !patient.aadhaarId.isEmpty()) {
            addWithTop(detail, text("Aadhaar ID  " + patient.aadhaarId,
                    11, TEXT_MUTED, Typeface.NORMAL), 4);
        }
        if (patient.abhaId != null && !patient.abhaId.isEmpty()) {
            addWithTop(detail, text("ABHA ID  " + patient.abhaId,
                    11, TEXT_MUTED, Typeface.NORMAL), 3);
        }

        AdminDataStore.SessionRecord latest = adminLatestSession(patient);
        LinearLayout snapshot = new LinearLayout(this);
        snapshot.setOrientation(LinearLayout.HORIZONTAL);
        snapshot.addView(adminMiniMetric(latest == null ? "—" :
                        formatShortDate(latest.startedAt), "LAST SESSION"),
                adminHalfParams(0, 5));
        snapshot.addView(adminMiniMetric(latest == null ? "—" :
                        latest.lastActiveFrequency() + " Hz", "LAST ACTIVE"),
                adminHalfParams(5, 0));
        addWithTop(detail, snapshot, 15);

        if (patient.review != null && !patient.review.trim().isEmpty()) {
            TextView review = text(patient.review, 12,
                    TEXT_MUTED, Typeface.NORMAL);
            review.setMaxLines(3);
            review.setBackground(roundRect(SURFACE_LOW, 13,
                    Color.TRANSPARENT, 0));
            review.setPadding(dp(12), dp(10), dp(12), dp(10));
            addWithTop(detail, review, 12);
        }

        if (!patient.previousReports.isEmpty()) {
            TextView reportsHeading = text("PREVIOUS REPORTS", 9,
                    MUTED, Typeface.BOLD);
            reportsHeading.setLetterSpacing(0.1f);
            addWithTop(detail, reportsHeading, 14);
            for (AdminDataStore.ReportAttachment report :
                    patient.previousReports) {
                Button openReport = secondaryButton(report.name);
                openReport.setOnClickListener(v -> openPatientReport(report));
                addWithTop(detail, openReport, 7);
            }
        }

        LinearLayout actions = new LinearLayout(this);
        Button session = primaryButton(connected ? "Start new session" :
                "Prepare session");
        setButtonIcon(session, R.drawable.ic_play, WHITE);
        session.setOnClickListener(v -> showNewSessionDialog(patient));
        attachPressAnimation(session);
        actions.addView(session, adminHalfParams(0, 5));
        Button edit = secondaryButton("Edit details");
        edit.setOnClickListener(v -> showEditPatientDialog(patient));
        attachPressAnimation(edit);
        actions.addView(edit, adminHalfParams(5, 0));
        addWithTop(detail, actions, 15);
        return detail;
    }

    private void openPatientReport(AdminDataStore.ReportAttachment report) {
        File file = new File(report.localPath == null ? "" : report.localPath);
        if (!file.exists()) {
            Toast.makeText(this, "This report file is no longer available.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, report.mimeType);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (ActivityNotFoundException error) {
            Toast.makeText(this, "No app is available to open this report.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private LinearLayout adminRecentActivityCard(AdminHistoryItem item) {
        LinearLayout recent = card();
        recent.setPadding(dp(16), dp(15), dp(16), dp(15));
        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(adminPatientAvatar(item.patient, false),
                new LinearLayout.LayoutParams(dp(42), dp(42)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text(item.patient.name + " · Session " +
                item.session.sessionNumber, 15, TEXT, Typeface.BOLD));
        addWithTop(copy, text(formatDateTime(item.session.startedAt),
                11, TEXT_MUTED, Typeface.NORMAL), 2);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        copyParams.setMargins(dp(11), 0, dp(8), 0);
        heading.addView(copy, copyParams);
        heading.addView(statusPill(formatDuration(item.session.durationMillis()),
                PRIMARY, PRIMARY_CONTAINER));
        recent.addView(heading);

        LinearLayout metrics = new LinearLayout(this);
        metrics.addView(adminMiniMetric(item.session.lastActiveFrequency() + " Hz",
                "LAST ACTIVE"), adminHalfParams(0, 5));
        metrics.addView(adminMiniMetric(String.valueOf(
                        item.session.frequencyMarks.size()), "CHANGES"),
                adminHalfParams(5, 0));
        addWithTop(recent, metrics, 12);
        recent.setClickable(true);
        recent.setFocusable(true);
        recent.setOnClickListener(v -> {
            adminPage = 1;
            showAdminWorkspace();
        });
        attachPressAnimation(recent);
        return recent;
    }

    private AdminDataStore.SessionRecord adminLatestSession(
            AdminDataStore.PatientRecord patient) {
        AdminDataStore.SessionRecord latest = null;
        for (AdminDataStore.SessionRecord session : patient.sessions) {
            if (latest == null || session.startedAt > latest.startedAt) {
                latest = session;
            }
        }
        return latest;
    }

    private AdminHistoryItem adminMostRecentSession() {
        AdminHistoryItem latest = null;
        for (AdminDataStore.PatientRecord patient : adminDataStore.patients()) {
            AdminDataStore.SessionRecord session = adminLatestSession(patient);
            if (session != null && (latest == null ||
                    session.startedAt > latest.session.startedAt)) {
                latest = new AdminHistoryItem(patient, session);
            }
        }
        return latest;
    }

    private int adminRecentSessionCount() {
        long cutoff = System.currentTimeMillis() - 7L * 24L * 60L * 60L * 1000L;
        int count = 0;
        for (AdminDataStore.PatientRecord patient : adminDataStore.patients()) {
            for (AdminDataStore.SessionRecord session : patient.sessions) {
                if (session.startedAt >= cutoff) {
                    count++;
                }
            }
        }
        return count;
    }

    private long adminTotalSessionDuration() {
        long total = 0L;
        for (AdminDataStore.PatientRecord patient : adminDataStore.patients()) {
            total += adminPatientSessionDuration(patient);
        }
        return total;
    }

    private long adminPatientSessionDuration(
            AdminDataStore.PatientRecord patient) {
        long total = 0L;
        for (AdminDataStore.SessionRecord session : patient.sessions) {
            total += session.durationMillis();
        }
        return total;
    }

    private String formatCompactDuration(long durationMillis) {
        long minutes = durationMillis / 60_000L;
        if (minutes < 60L) {
            return minutes + "m";
        }
        long hours = minutes / 60L;
        long remainingMinutes = minutes % 60L;
        return remainingMinutes == 0L ? hours + "h" :
                hours + "h " + remainingMinutes + "m";
    }

    private String formatShortDate(long timestamp) {
        return new SimpleDateFormat("dd MMM", Locale.getDefault())
                .format(new Date(timestamp));
    }

    private LinearLayout adminPageTabs() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setPadding(dp(14), dp(8), dp(14), dp(8));
        wrap.setBackgroundColor(WHITE);
        LinearLayout tabs = new LinearLayout(this);
        tabs.setPadding(dp(3), dp(3), dp(3), dp(3));
        tabs.setBackground(roundRect(SURFACE_LOW, 16, OUTLINE, 1));
        String[] labels = {"Overview", "History", "Records", "PTP"};
        for (int index = 0; index < labels.length; index++) {
            final int page = index;
            Button tab = plainButton(labels[index]);
            tab.setTextSize(12);
            tab.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            tab.setTextColor(adminPage == index ? WHITE : TEXT_MUTED);
            tab.setBackground(adminPage == index
                    ? rippleBackground(PRIMARY, 13)
                    : rippleBackground(Color.TRANSPARENT, 13));
            tab.setOnClickListener(v -> {
                adminPage = page;
                showAdminWorkspace();
            });
            tabs.addView(tab, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        }
        wrap.addView(tabs, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        return wrap;
    }

    private void showAdminHistoryPage() {
        LinearLayout root = adminPageRoot();
        LinearLayout content = pageColumn();
        content.addView(text("Session history", 30, TEXT, Typeface.BOLD));
        addWithTop(content, text(
                "Compare outcomes, timing, and every recorded frequency change.",
                14, TEXT_MUTED, Typeface.NORMAL), 6);

        ArrayList<AdminHistoryItem> items = new ArrayList<>();
        for (AdminDataStore.PatientRecord patient : adminDataStore.patients()) {
            for (AdminDataStore.SessionRecord session : patient.sessions) {
                items.add(new AdminHistoryItem(patient, session));
            }
        }
        Collections.sort(items, (left, right) -> Long.compare(
                right.session.startedAt, left.session.startedAt));

        LinearLayout summary = tintedCard();
        summary.setOrientation(LinearLayout.HORIZONTAL);
        summary.addView(adminSummaryMetric(String.valueOf(items.size()),
                "TOTAL SESSIONS"), adminThirdParams(0, 5));
        summary.addView(adminSummaryMetric(formatCompactDuration(
                        adminTotalSessionDuration()), "RECORDED TIME"),
                adminThirdParams(5, 5));
        summary.addView(adminSummaryMetric(String.valueOf(
                        adminDataStore.patients().size()), "PATIENTS"),
                adminThirdParams(5, 0));
        addWithTop(content, summary, 18);

        if (items.isEmpty()) {
            addWithTop(content, adminEmptyState(
                    "No recorded sessions",
                    "Completed sessions will appear here with duration, the patient's complaint, and frequency timeline."),
                    20);
        } else {
            addWithTop(content, sectionEyebrow(
                    "LATEST SESSION FIRST"), 24);
            for (AdminHistoryItem item : items) {
                addWithTop(content, adminHistoryCard(item), 10);
            }
        }
        finishAdminPage(root, content);
    }

    private void showAdminExportPage() {
        LinearLayout root = adminPageRoot();
        LinearLayout content = pageColumn();
        content.addView(text("Records & export", 30, TEXT, Typeface.BOLD));
        addWithTop(content, text(
                "Create a portable Excel record or manage data stored on this device.",
                14, TEXT_MUTED, Typeface.NORMAL), 6);

        LinearLayout exportInfo = tintedCard();
        exportInfo.setOrientation(LinearLayout.HORIZONTAL);
        exportInfo.setGravity(Gravity.CENTER_VERTICAL);
        ImageView exportIcon = icon(R.drawable.ic_info, PRIMARY, 22);
        exportIcon.setBackground(circleDrawable(WHITE));
        exportIcon.setPadding(dp(9), dp(9), dp(9), dp(9));
        exportInfo.addView(exportIcon, new LinearLayout.LayoutParams(dp(42), dp(42)));
        LinearLayout exportCopy = new LinearLayout(this);
        exportCopy.setOrientation(LinearLayout.VERTICAL);
        exportCopy.addView(text("Complete, clinician-friendly export", 14,
                TEXT, Typeface.BOLD));
        addWithTop(exportCopy, text(
                "Includes patient details, session notes, duration, and frequency timeline.",
                12, TEXT_MUTED, Typeface.NORMAL), 3);
        LinearLayout.LayoutParams exportCopyParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        exportCopyParams.setMargins(dp(12), 0, 0, 0);
        exportInfo.addView(exportCopy, exportCopyParams);
        addWithTop(content, exportInfo, 18);

        AdminDataStore.PatientRecord selected =
                adminDataStore.findPatient(selectedAdminPatientId);
        if (selected != null) {
            addWithTop(content, sectionEyebrow("RECORD ACTIONS"), 22);
            addWithTop(content, adminRecordActionsCard(selected), 10);
        }

        addWithTop(content, sectionEyebrow("PATIENT RECORDS"), 26);
        addWithTop(content, text(
                "Tap to select · Hold a patient for quick record actions",
                12, TEXT_MUTED, Typeface.NORMAL), 6);
        if (adminDataStore.patients().isEmpty()) {
            addWithTop(content, adminEmptyState(
                    "No patients to export",
                    "Add a patient on the Overview page before exporting records."), 10);
        } else {
            for (AdminDataStore.PatientRecord patient : adminDataStore.patients()) {
                addWithTop(content, adminPatientCard(patient), 10);
            }
        }
        finishAdminPage(root, content);
    }

    private void showAdminPtpPage() {
        LinearLayout root = adminPageRoot();
        LinearLayout content = pageColumn();
        content.addView(text("Pass to Patient", 30, TEXT, Typeface.BOLD));
        addWithTop(content, text(
                "Assign three safe frequencies before handing the app to a patient.",
                14, TEXT_MUTED, Typeface.NORMAL), 6);

        LinearLayout status = new LinearLayout(this);
        status.setOrientation(LinearLayout.HORIZONTAL);
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.setPadding(dp(16), dp(14), dp(16), dp(14));
        status.setBackground(roundRect(ptpEnabled
                        ? Color.rgb(235, 249, 239) : SURFACE_LOW,
                18, ptpEnabled ? SUCCESS : OUTLINE, 1));
        ImageView statusIcon = icon(ptpEnabled
                        ? R.drawable.ic_signal : R.drawable.ic_info,
                ptpEnabled ? SUCCESS : PRIMARY, 21);
        status.addView(statusIcon, new LinearLayout.LayoutParams(dp(34), dp(34)));
        LinearLayout statusCopy = new LinearLayout(this);
        statusCopy.setOrientation(LinearLayout.VERTICAL);
        statusCopy.addView(text(ptpEnabled ? "Patient mode active" :
                "Patient mode not active", 15, TEXT, Typeface.BOLD));
        addWithTop(statusCopy, text(ptpEnabled
                        ? ptpPatientName + " can use only the three approved levels."
                        : "Save an assignment to lock Control to Low, Medium, and High.",
                12, TEXT_MUTED, Typeface.NORMAL), 3);
        LinearLayout.LayoutParams statusCopyParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        statusCopyParams.setMargins(dp(10), 0, 0, 0);
        status.addView(statusCopy, statusCopyParams);
        addWithTop(content, status, 18);

        LinearLayout assignment = card();
        assignment.addView(text("Patient assignment", 20, TEXT, Typeface.BOLD));
        addWithTop(assignment, text(
                "These details identify who the locked preset set is for.",
                12, TEXT_MUTED, Typeface.NORMAL), 4);
        EditText name = adminField("Enter patient name",
                android.text.InputType.TYPE_CLASS_TEXT |
                        android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS, false);
        name.setText(ptpPatientName);
        EditText phone = adminField("Enter phone number",
                android.text.InputType.TYPE_CLASS_PHONE, false);
        phone.setText(ptpPatientPhone);
        LinearLayout patientActions = new LinearLayout(this);
        Button choosePatient = secondaryButton("Choose existing patient");
        choosePatient.setEnabled(!adminDataStore.patients().isEmpty());
        choosePatient.setAlpha(adminDataStore.patients().isEmpty() ? 0.45f : 1f);
        choosePatient.setOnClickListener(v ->
                showPtpPatientPicker(name, phone));
        attachPressAnimation(choosePatient);
        Button enrollPatient = secondaryButton("Enroll new patient");
        enrollPatient.setOnClickListener(v -> showEnrollPatientDialog(true));
        attachPressAnimation(enrollPatient);
        patientActions.addView(choosePatient, adminHalfParams(0, 5));
        patientActions.addView(enrollPatient, adminHalfParams(5, 0));
        addWithTop(assignment, patientActions, 16);
        addWithTop(assignment, adminDialogFieldBlock(
                "PATIENT NAME", "Choose an enrolled patient or enter details manually", name), 14);
        addWithTop(assignment, adminDialogFieldBlock(
                "PHONE NUMBER", null, phone), 12);
        addWithTop(content, assignment, 14);

        LinearLayout levels = card();
        levels.addView(text("Approved quick levels", 20, TEXT, Typeface.BOLD));
        addWithTop(levels, text(
                "Set three increasing values between 1 and " + MAX_VALUE + " Hz.",
                12, TEXT_MUTED, Typeface.NORMAL), 4);
        EditText low = adminField("Low frequency",
                android.text.InputType.TYPE_CLASS_NUMBER, false);
        low.setText(String.valueOf(ptpLowFrequency));
        EditText medium = adminField("Medium frequency",
                android.text.InputType.TYPE_CLASS_NUMBER, false);
        medium.setText(String.valueOf(ptpMediumFrequency));
        EditText high = adminField("High frequency",
                android.text.InputType.TYPE_CLASS_NUMBER, false);
        high.setText(String.valueOf(ptpHighFrequency));
        addWithTop(levels, adminDialogFieldBlock(
                "LOW · HZ", "Gentlest approved level", low), 16);
        addWithTop(levels, adminDialogFieldBlock(
                "MEDIUM · HZ", "Everyday approved level", medium), 12);
        addWithTop(levels, adminDialogFieldBlock(
                "HIGH · HZ", "Strongest approved level", high), 12);
        addWithTop(content, levels, 14);

        Button save = primaryButton(ptpEnabled
                ? "Update patient mode" : "Save & activate PTP");
        setButtonIcon(save, R.drawable.ic_arrow_forward, WHITE);
        save.setOnClickListener(v -> {
            String patientName = name.getText().toString().trim();
            String patientPhone = phone.getText().toString().trim();
            if (patientName.isEmpty()) {
                name.setError("Patient name is required");
                return;
            }
            if (patientPhone.isEmpty()) {
                phone.setError("Phone number is required");
                return;
            }
            int lowValue = parsePtpFrequency(low);
            int mediumValue = parsePtpFrequency(medium);
            int highValue = parsePtpFrequency(high);
            if (lowValue < 0 || mediumValue < 0 || highValue < 0) {
                return;
            }
            if (!(lowValue < mediumValue && mediumValue < highValue)) {
                high.setError("Use increasing values: Low < Medium < High");
                return;
            }
            if (connected && currentMotorValue > 0) {
                writeMotorValue(0);
            }
            ptpActivityStore.finishConnection(System.currentTimeMillis());
            ptpPatientName = patientName;
            ptpPatientPhone = patientPhone;
            ptpLowFrequency = lowValue;
            ptpMediumFrequency = mediumValue;
            ptpHighFrequency = highValue;
            ptpEnabled = true;
            currentMotorValue = 0;
            preferences.edit()
                    .putBoolean("ptp_enabled", true)
                    .putString("ptp_patient_name", ptpPatientName)
                    .putString("ptp_patient_phone", ptpPatientPhone)
                    .putInt("ptp_low_frequency", ptpLowFrequency)
                    .putInt("ptp_medium_frequency", ptpMediumFrequency)
                    .putInt("ptp_high_frequency", ptpHighFrequency)
                    .apply();
            if (connected) {
                ptpActivityStore.startConnection(
                        ptpPatientName, ptpPatientPhone,
                        System.currentTimeMillis());
                ptpActivityStore.recordFrequency(0, System.currentTimeMillis());
            }
            Toast.makeText(this,
                    "Patient mode activated for " + ptpPatientName,
                    Toast.LENGTH_SHORT).show();
            showAdminPtpPage();
        });
        attachPressAnimation(save);
        addWithTop(content, save, 18);

        if (ptpEnabled) {
            Button disable = secondaryButton("Disable patient mode");
            disable.setTextColor(ERROR);
            disable.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("Disable patient mode?")
                    .setMessage("This restores unrestricted frequency control and the full preset library.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Disable", (dialog, which) -> {
                        if (connected && currentMotorValue > 0) {
                            writeMotorValue(0);
                        }
                        ptpActivityStore.finishConnection(
                                System.currentTimeMillis());
                        ptpEnabled = false;
                        currentMotorValue = 0;
                        preferences.edit().putBoolean("ptp_enabled", false).apply();
                        showAdminPtpPage();
                    })
                    .show());
            attachPressAnimation(disable);
            addWithTop(content, disable, 10);
        }

        TextView safety = text(
                "When PTP is active, the session can be stopped at any time but can only start at the three administrator-approved levels.",
                11, TEXT_MUTED, Typeface.NORMAL);
        safety.setGravity(Gravity.CENTER);
        safety.setPadding(dp(14), dp(16), dp(14), dp(4));
        content.addView(safety);
        showPtpTrackerSection(content);
        finishAdminPage(root, content);
    }

    private void showPtpPatientPicker(EditText name, EditText phone) {
        List<AdminDataStore.PatientRecord> patients = adminDataStore.patients();
        if (patients.isEmpty()) {
            Toast.makeText(this,
                    "Enroll a patient before choosing from the directory.",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labels = new String[patients.size()];
        for (int index = 0; index < patients.size(); index++) {
            AdminDataStore.PatientRecord patient = patients.get(index);
            labels[index] = patient.name + "  ·  " + patient.phoneNumber;
        }
        new AlertDialog.Builder(this)
                .setTitle("Choose patient for PTP")
                .setItems(labels, (dialog, which) -> {
                    AdminDataStore.PatientRecord patient = patients.get(which);
                    selectedAdminPatientId = patient.id;
                    name.setText(patient.name);
                    phone.setText(patient.phoneNumber);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showPtpTrackerSection(LinearLayout content) {
        addWithTop(content, sectionEyebrow("PATIENT ACTIVITY TRACKER"), 26);
        addWithTop(content, text(
                "A local audit trail of PTP connections and level changes applied in NeuroVibe.",
                12, TEXT_MUTED, Typeface.NORMAL), 6);

        LinearLayout summary = new LinearLayout(this);
        summary.setOrientation(LinearLayout.HORIZONTAL);
        summary.addView(adminSummaryMetric(
                        String.valueOf(ptpActivityStore.recordCount()),
                        "CONNECTIONS"),
                adminThirdParams(0, 4));
        summary.addView(adminSummaryMetric(
                        formatCompactDuration(
                                ptpActivityStore.totalActiveDurationMillis()),
                        "ACTIVE USE"),
                adminThirdParams(4, 4));
        List<PtpActivityStore.ActivityRecord> records =
                ptpActivityStore.recordsNewestFirst();
        summary.addView(adminSummaryMetric(records.isEmpty() ? "—" :
                        records.get(0).lastActiveFrequency() + " Hz",
                        "LAST LEVEL"),
                adminThirdParams(4, 0));
        summary.setPadding(dp(8), dp(8), dp(8), dp(8));
        summary.setBackground(roundRect(WHITE, 18, OUTLINE, 1));
        addWithTop(content, summary, 12);

        Button export = primaryButton("Download activity as Excel");
        setButtonIcon(export, R.drawable.ic_arrow_forward, WHITE);
        export.setEnabled(!records.isEmpty());
        export.setAlpha(records.isEmpty() ? 0.45f : 1f);
        export.setOnClickListener(v -> exportPtpActivity());
        attachPressAnimation(export);
        addWithTop(content, export, 12);

        Button deleteHistory = secondaryButton("Delete all PTP history");
        deleteHistory.setTextColor(ERROR);
        deleteHistory.setEnabled(!records.isEmpty());
        deleteHistory.setAlpha(records.isEmpty() ? 0.45f : 1f);
        deleteHistory.setOnClickListener(v -> {
            ptpActivityStore.clearAll();
            Toast.makeText(this, "All PTP activity history deleted.",
                    Toast.LENGTH_SHORT).show();
            showAdminPtpPage();
        });
        attachPressAnimation(deleteHistory);
        addWithTop(content, deleteHistory, 10);

        if (records.isEmpty()) {
            addWithTop(content, adminEmptyState(
                    "No PTP activity yet",
                    "A record will appear after the assigned patient connects to NeuroVibe."), 12);
        } else {
            for (PtpActivityStore.ActivityRecord record : records) {
                addWithTop(content, ptpActivityCard(record), 10);
            }
        }

        TextView accuracy = text(
                "The tracker records successful level changes from this app. It cannot verify physical placement or wear of the device.",
                11, TEXT_MUTED, Typeface.NORMAL);
        accuracy.setGravity(Gravity.CENTER);
        accuracy.setPadding(dp(12), dp(10), dp(12), dp(4));
        content.addView(accuracy);
    }

    private LinearLayout ptpActivityCard(
            PtpActivityStore.ActivityRecord record) {
        LinearLayout wrapper = card();
        wrapper.setPadding(dp(16), dp(16), dp(16), dp(16));

        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView avatar = text(record.patientName == null ||
                        record.patientName.isEmpty() ? "?" :
                        record.patientName.substring(0, 1).toUpperCase(Locale.US),
                17, PRIMARY, Typeface.BOLD);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(circleDrawable(PRIMARY_CONTAINER));
        heading.addView(avatar, new LinearLayout.LayoutParams(dp(42), dp(42)));
        LinearLayout identity = new LinearLayout(this);
        identity.setOrientation(LinearLayout.VERTICAL);
        identity.addView(text(record.patientName, 15, TEXT, Typeface.BOLD));
        addWithTop(identity, text(record.phoneNumber, 11,
                TEXT_MUTED, Typeface.NORMAL), 2);
        LinearLayout.LayoutParams identityParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        identityParams.setMargins(dp(10), 0, dp(8), 0);
        heading.addView(identity, identityParams);
        heading.addView(statusPill(record.disconnectedAt == 0
                        ? "CONNECTED" : "COMPLETE",
                record.disconnectedAt == 0 ? SUCCESS : PRIMARY,
                record.disconnectedAt == 0
                        ? Color.rgb(235, 249, 239) : PRIMARY_CONTAINER));
        wrapper.addView(heading);

        addWithTop(wrapper, text("Connected  " +
                        formatDateTime(record.connectedAt),
                12, TEXT, Typeface.BOLD), 13);
        addWithTop(wrapper, text("Disconnected  " +
                        (record.disconnectedAt > 0
                                ? formatDateTime(record.disconnectedAt)
                                : "Still connected"),
                12, TEXT_MUTED, Typeface.NORMAL), 4);

        LinearLayout metrics = new LinearLayout(this);
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        metrics.addView(adminSummaryMetric(
                        formatCompactDuration(record.connectionDurationMillis()),
                        "CONNECTED"), adminThirdParams(0, 4));
        metrics.addView(adminSummaryMetric(
                        formatCompactDuration(record.activeDurationMillis()),
                        "ACTIVE USE"), adminThirdParams(4, 4));
        metrics.addView(adminSummaryMetric(
                        record.lastActiveFrequency() + " Hz",
                        "LAST LEVEL"), adminThirdParams(4, 0));
        metrics.setPadding(0, dp(4), 0, dp(4));
        metrics.setBackground(roundRect(SURFACE_LOW, 14,
                Color.TRANSPARENT, 0));
        addWithTop(wrapper, metrics, 12);

        TextView timelineTitle = text("FREQUENCY TIMELINE", 9,
                MUTED, Typeface.BOLD);
        timelineTitle.setLetterSpacing(0.1f);
        addWithTop(wrapper, timelineTitle, 14);
        if (record.frequencyMarks.isEmpty()) {
            addWithTop(wrapper, text("Connected, but no level was selected.",
                    12, TEXT_MUTED, Typeface.NORMAL), 6);
        } else {
            for (int index = 0; index < record.frequencyMarks.size(); index++) {
                PtpActivityStore.FrequencyMark mark =
                        record.frequencyMarks.get(index);
                long nextTimestamp = index + 1 < record.frequencyMarks.size()
                        ? record.frequencyMarks.get(index + 1).timestamp
                        : record.disconnectedAt > 0
                        ? record.disconnectedAt : System.currentTimeMillis();
                String detail = mark.frequency == 0
                        ? "Session stopped"
                        : mark.frequency + " Hz for " + formatCompactDuration(
                        Math.max(0L, nextTimestamp - mark.timestamp));
                addWithTop(wrapper, text(
                        formatClockTime(mark.timestamp) + "  ·  " + detail,
                        12, mark.frequency == 0 ? TEXT_MUTED : PRIMARY,
                        mark.frequency == 0 ? Typeface.NORMAL : Typeface.BOLD), 6);
            }
        }
        return wrapper;
    }

    private int parsePtpFrequency(EditText field) {
        String raw = field.getText().toString().trim();
        if (raw.isEmpty()) {
            field.setError("Frequency is required");
            return -1;
        }
        try {
            int value = Integer.parseInt(raw);
            if (value < 1 || value > MAX_VALUE) {
                field.setError("Enter a value from 1 to " + MAX_VALUE);
                return -1;
            }
            return value;
        } catch (NumberFormatException ignored) {
            field.setError("Enter a valid frequency");
            return -1;
        }
    }

    private LinearLayout adminRecordActionsCard(
            AdminDataStore.PatientRecord patient) {
        LinearLayout detail = card();
        detail.setBackground(roundRect(WHITE, 22, PRIMARY, 1));
        LinearLayout selectedHeading = new LinearLayout(this);
        selectedHeading.setGravity(Gravity.CENTER_VERTICAL);
        selectedHeading.addView(adminPatientAvatar(patient, true),
                new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout selectedCopy = new LinearLayout(this);
        selectedCopy.setOrientation(LinearLayout.VERTICAL);
        selectedCopy.addView(text(patient.name, 20, TEXT, Typeface.BOLD));
        addWithTop(selectedCopy, text(patient.phoneNumber, 12,
                TEXT_MUTED, Typeface.NORMAL), 2);
        LinearLayout.LayoutParams selectedCopyParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        selectedCopyParams.setMargins(dp(12), 0, 0, 0);
        selectedHeading.addView(selectedCopy, selectedCopyParams);
        selectedHeading.addView(statusPill(patient.sessions.size() +
                        (patient.sessions.size() == 1 ? " session" : " sessions"),
                PRIMARY, PRIMARY_CONTAINER));
        detail.addView(selectedHeading);
        addWithTop(detail, text(patient.sessions.size() +
                        " recorded sessions  ·  " +
                        formatCompactDuration(adminPatientSessionDuration(patient)) +
                        " total",
                13, TEXT_MUTED, Typeface.NORMAL), 8);

        Button export = primaryButton("Export Excel record");
        setButtonIcon(export, R.drawable.ic_arrow_forward, WHITE);
        export.setOnClickListener(v -> exportPatientHistory(patient));
        attachPressAnimation(export);
        addWithTop(detail, export, 18);

        Button clear = secondaryButton("Delete session history");
        clear.setTextColor(ERROR);
        clear.setEnabled(!patient.sessions.isEmpty());
        clear.setOnClickListener(v -> confirmDeleteSessionHistory(patient));
        attachPressAnimation(clear);
        addWithTop(detail, clear, 10);

        Button delete = secondaryButton("Delete patient and history");
        delete.setTextColor(ERROR);
        delete.setOnClickListener(v -> confirmDeletePatient(patient));
        attachPressAnimation(delete);
        addWithTop(detail, delete, 10);

        TextView warning = text(
                "Deletion is permanent on this device and always requires confirmation.",
                11, ERROR, Typeface.NORMAL);
        warning.setGravity(Gravity.CENTER);
        addWithTop(detail, warning, 12);
        return detail;
    }

    private LinearLayout adminPageRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.addView(adminTopBar("Admin workspace", "Local clinical records", false),
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(86)));
        root.addView(adminPageTabs(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(62)));
        return root;
    }

    private void finishAdminPage(LinearLayout root, LinearLayout content) {
        ScrollView scroll = scroll(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        springPageIn(scroll);
    }

    private LinearLayout adminEmptyState(String title, String body) {
        LinearLayout empty = card();
        empty.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageView emptyIcon = icon(R.drawable.ic_brain_circuit, PRIMARY, 30);
        emptyIcon.setPadding(dp(14), dp(14), dp(14), dp(14));
        emptyIcon.setBackground(circleDrawable(PRIMARY_CONTAINER));
        empty.addView(emptyIcon, new LinearLayout.LayoutParams(dp(58), dp(58)));
        TextView titleView = text(title, 19, TEXT, Typeface.BOLD);
        titleView.setGravity(Gravity.CENTER);
        addWithTop(empty, titleView, 12);
        TextView bodyView = text(body, 13, TEXT_MUTED, Typeface.NORMAL);
        bodyView.setGravity(Gravity.CENTER);
        addWithTop(empty, bodyView, 4);
        return empty;
    }

    private void confirmDeleteSessionHistory(AdminDataStore.PatientRecord patient) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Delete all session history?")
                .setMessage("This permanently removes " + patient.sessions.size() +
                        " sessions for " + patient.name + ". The patient profile remains.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete history", (dialogInterface, which) -> {
                    adminDataStore.deleteSessionHistory(patient);
                    showAdminWorkspace();
                })
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(
                AlertDialog.BUTTON_POSITIVE).setTextColor(ERROR));
        dialog.show();
    }

    private void confirmDeletePatient(AdminDataStore.PatientRecord patient) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Delete " + patient.name + "?")
                .setMessage("The patient profile and every recorded session will be permanently deleted from this device.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete patient", (dialogInterface, which) -> {
                    adminDataStore.deletePatient(patient.id);
                    selectedAdminPatientId = adminDataStore.patients().isEmpty()
                            ? null : adminDataStore.patients().get(0).id;
                    showAdminWorkspace();
                })
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(
                AlertDialog.BUTTON_POSITIVE).setTextColor(ERROR));
        dialog.show();
    }

    private void showPatientRecordActionsDialog(
            AdminDataStore.PatientRecord patient) {
        selectedAdminPatientId = patient.id;
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(22), dp(22), dp(22), dp(20));
        panel.setBackground(roundRect(WHITE, 24, Color.TRANSPARENT, 0));

        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(adminPatientAvatar(patient, true),
                new LinearLayout.LayoutParams(dp(54), dp(54)));
        LinearLayout headingCopy = new LinearLayout(this);
        headingCopy.setOrientation(LinearLayout.VERTICAL);
        headingCopy.addView(text("Record actions", 23, TEXT, Typeface.BOLD));
        addWithTop(headingCopy, text(patient.name, 13,
                TEXT_MUTED, Typeface.NORMAL), 3);
        LinearLayout.LayoutParams headingCopyParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        headingCopyParams.setMargins(dp(13), 0, dp(8), 0);
        heading.addView(headingCopy, headingCopyParams);
        heading.addView(statusPill(patient.sessions.size() +
                        (patient.sessions.size() == 1 ? " session" : " sessions"),
                PRIMARY, PRIMARY_CONTAINER));
        panel.addView(heading);

        TextView hint = text(
                "Choose an action for this locally stored patient record.",
                12, TEXT_MUTED, Typeface.NORMAL);
        hint.setPadding(dp(12), dp(10), dp(12), dp(10));
        hint.setBackground(roundRect(SURFACE_LOW, 13,
                Color.TRANSPARENT, 0));
        addWithTop(panel, hint, 16);

        LinearLayout export = adminRecordActionRow(
                R.drawable.ic_arrow_forward,
                "Export Excel record",
                "Patient details, sessions, complaints, and frequency timeline",
                PRIMARY, PRIMARY_CONTAINER);
        addWithTop(panel, export, 16);

        LinearLayout clear = adminRecordActionRow(
                R.drawable.ic_info,
                "Delete session history",
                "Keep the patient profile but remove every completed session",
                ERROR, ERROR_LIGHT);
        clear.setEnabled(!patient.sessions.isEmpty());
        clear.setAlpha(patient.sessions.isEmpty() ? 0.42f : 1f);
        addWithTop(panel, clear, 10);

        LinearLayout delete = adminRecordActionRow(
                R.drawable.ic_stop,
                "Delete patient and history",
                "Permanently remove the profile and all associated sessions",
                ERROR, ERROR_LIGHT);
        addWithTop(panel, delete, 10);

        Button close = secondaryButton("Close");
        addWithTop(panel, close, 18);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(panel)
                .create();
        export.setOnClickListener(v -> {
            dialog.dismiss();
            exportPatientHistory(patient);
        });
        clear.setOnClickListener(v -> {
            dialog.dismiss();
            confirmDeleteSessionHistory(patient);
        });
        delete.setOnClickListener(v -> {
            dialog.dismiss();
            confirmDeletePatient(patient);
        });
        close.setOnClickListener(v -> dialog.dismiss());
        dialog.setOnShowListener(ignored -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(
                        new ColorDrawable(Color.TRANSPARENT));
                dialog.getWindow().setLayout(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        });
        dialog.show();
    }

    private LinearLayout adminRecordActionRow(int iconResource, String title,
                                               String detail, int color,
                                               int background) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(13), dp(14), dp(13));
        row.setBackground(rippleBackground(background, 16));
        row.setClickable(true);
        row.setFocusable(true);

        ImageView actionIcon = icon(iconResource, color, 20);
        actionIcon.setPadding(dp(9), dp(9), dp(9), dp(9));
        actionIcon.setBackground(circleDrawable(WHITE));
        row.addView(actionIcon, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text(title, 15, color, Typeface.BOLD));
        addWithTop(copy, text(detail, 11, TEXT_MUTED, Typeface.NORMAL), 3);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        copyParams.setMargins(dp(12), 0, dp(8), 0);
        row.addView(copy, copyParams);

        TextView arrow = text("›", 24, color, Typeface.NORMAL);
        arrow.setGravity(Gravity.CENTER);
        row.addView(arrow, new LinearLayout.LayoutParams(dp(24), dp(42)));
        row.setContentDescription(title + ". " + detail);
        attachPressAnimation(row);
        return row;
    }

    private View adminTopBar(String title, String subtitle, boolean sessionRunning) {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(14), dp(10), dp(14), dp(6));
        bar.setBackgroundColor(WHITE);
        Button back = plainButton(sessionRunning ? "Admin" : "‹ Settings");
        back.setEnabled(!sessionRunning);
        back.setTextColor(sessionRunning ? MUTED : PRIMARY);
        if (!sessionRunning) {
            back.setOnClickListener(v -> closeAdminWorkspace());
        }
        bar.addView(back, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.CENTER);
        TextView titleView = text(title, 17, TEXT, Typeface.BOLD);
        titleView.setGravity(Gravity.CENTER);
        copy.addView(titleView);
        TextView subtitleView = text(subtitle, 11, TEXT_MUTED, Typeface.NORMAL);
        subtitleView.setGravity(Gravity.CENTER);
        copy.addView(subtitleView);
        bar.addView(copy, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button lock = plainButton(sessionRunning ? "Running" : "Lock");
        lock.setTextColor(sessionRunning ? SUCCESS : PRIMARY);
        lock.setEnabled(!sessionRunning);
        if (!sessionRunning) {
            lock.setOnClickListener(v -> closeAdminWorkspace());
        }
        bar.addView(lock, new LinearLayout.LayoutParams(dp(76), dp(48)));
        return bar;
    }

    private LinearLayout adminStatCard(String value, String label) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(8), dp(14), dp(8), dp(14));
        card.setBackground(roundRect(WHITE, 18, OUTLINE, 1));
        TextView valueView = text(value, 17, TEXT, Typeface.BOLD);
        valueView.setGravity(Gravity.CENTER);
        card.addView(valueView);
        TextView labelView = text(label, 9, MUTED, Typeface.BOLD);
        labelView.setLetterSpacing(0.1f);
        labelView.setGravity(Gravity.CENTER);
        addWithTop(card, labelView, 4);
        return card;
    }

    private LinearLayout adminSummaryMetric(String value, String label) {
        LinearLayout metric = new LinearLayout(this);
        metric.setOrientation(LinearLayout.VERTICAL);
        metric.setGravity(Gravity.CENTER);
        metric.setPadding(dp(4), dp(8), dp(4), dp(8));
        TextView valueView = text(value, 16, PRIMARY, Typeface.BOLD);
        valueView.setGravity(Gravity.CENTER);
        metric.addView(valueView);
        TextView labelView = text(label, 8, MUTED, Typeface.BOLD);
        labelView.setLetterSpacing(0.08f);
        labelView.setGravity(Gravity.CENTER);
        addWithTop(metric, labelView, 3);
        return metric;
    }

    private LinearLayout.LayoutParams adminThirdParams(int left, int right) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(dp(left), 0, dp(right), 0);
        return params;
    }

    private LinearLayout.LayoutParams adminHalfParams(int left, int right) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, dp(56), 1f);
        params.setMargins(dp(left), 0, dp(right), 0);
        return params;
    }

    private LinearLayout.LayoutParams adminFormHalfParams(int left, int right) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(dp(left), 0, dp(right), 0);
        return params;
    }

    private LinearLayout adminPatientCard(AdminDataStore.PatientRecord patient) {
        boolean selected = patient.id.equals(selectedAdminPatientId);
        LinearLayout card = card();
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(roundRect(selected ? SURFACE_HIGH : WHITE,
                20, selected ? PRIMARY : OUTLINE, selected ? 2 : 1));
        card.addView(adminPatientAvatar(patient, selected),
                new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text(patient.name, 17, TEXT, Typeface.BOLD));
        AdminDataStore.SessionRecord latest = adminLatestSession(patient);
        String activity = latest == null ? "No sessions yet" :
                "Last session " + formatShortDate(latest.startedAt);
        addWithTop(copy, text(patient.phoneNumber + "  ·  " + activity,
                12, TEXT_MUTED, Typeface.NORMAL), 3);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        copyParams.setMargins(dp(12), 0, dp(8), 0);
        card.addView(copy, copyParams);
        TextView action = text(selected ? "Selected" : "Select ›",
                13, selected ? SUCCESS : PRIMARY, Typeface.BOLD);
        card.addView(action);
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> {
            selectedAdminPatientId = patient.id;
            showAdminWorkspace();
        });
        if (adminPage == 2) {
            card.setTooltipText("Hold for record actions");
            card.setOnLongClickListener(v -> {
                showPatientRecordActionsDialog(patient);
                return true;
            });
        }
        attachPressAnimation(card);
        return card;
    }

    private TextView adminPatientAvatar(AdminDataStore.PatientRecord patient,
                                        boolean selected) {
        TextView avatar = text(patient.name == null || patient.name.isEmpty()
                        ? "?" : patient.name.substring(0, 1).toUpperCase(Locale.US),
                18, selected ? WHITE : PRIMARY, Typeface.BOLD);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(circleDrawable(selected ? PRIMARY : PRIMARY_CONTAINER));
        return avatar;
    }

    private LinearLayout adminHistoryCard(AdminHistoryItem item) {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setPadding(dp(14), dp(14), dp(14), dp(14));
        wrapper.setBackground(roundRect(WHITE, 20, OUTLINE, 1));

        LinearLayout identity = new LinearLayout(this);
        identity.setGravity(Gravity.CENTER_VERTICAL);
        identity.addView(adminPatientAvatar(item.patient, false),
                new LinearLayout.LayoutParams(dp(40), dp(40)));
        LinearLayout identityCopy = new LinearLayout(this);
        identityCopy.setOrientation(LinearLayout.VERTICAL);
        identityCopy.addView(text(item.patient.name, 15, TEXT, Typeface.BOLD));
        addWithTop(identityCopy, text(item.patient.phoneNumber, 11,
                TEXT_MUTED, Typeface.NORMAL), 1);
        LinearLayout.LayoutParams identityCopyParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        identityCopyParams.setMargins(dp(10), 0, dp(8), 0);
        identity.addView(identityCopy, identityCopyParams);
        TextView sessionLabel = text("SESSION " + item.session.sessionNumber,
                10, PRIMARY, Typeface.BOLD);
        sessionLabel.setLetterSpacing(0.07f);
        sessionLabel.setPadding(dp(9), dp(6), dp(9), dp(6));
        sessionLabel.setBackground(roundRect(PRIMARY_CONTAINER, 99,
                Color.TRANSPARENT, 0));
        identity.addView(sessionLabel);
        wrapper.addView(identity);

        LinearLayout sessionCard = adminSessionHistoryCard(item.session);
        sessionCard.setBackground(roundRect(SURFACE_LOW, 15,
                Color.TRANSPARENT, 0));
        addWithTop(wrapper, sessionCard, 12);
        return wrapper;
    }

    private LinearLayout adminSessionHistoryCard(
            AdminDataStore.SessionRecord session) {
        LinearLayout card = card();
        card.setPadding(dp(16), dp(15), dp(16), dp(15));
        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titleCopy = new LinearLayout(this);
        titleCopy.setOrientation(LinearLayout.VERTICAL);
        titleCopy.addView(text("Session " + session.sessionNumber,
                17, TEXT, Typeface.BOLD));
        addWithTop(titleCopy, text(formatDateTime(session.startedAt),
                11, TEXT_MUTED, Typeface.NORMAL), 2);
        heading.addView(titleCopy, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        heading.addView(statusPill(formatDuration(session.durationMillis()),
                PRIMARY, PRIMARY_CONTAINER));
        card.addView(heading);

        LinearLayout metrics = new LinearLayout(this);
        int initial = session.frequencyMarks.isEmpty()
                ? 0 : session.frequencyMarks.get(0).frequency;
        metrics.addView(adminMiniMetric(initial + " Hz", "START"),
                adminThirdParams(0, 5));
        metrics.addView(adminMiniMetric(session.frequencyMarks.size() + "",
                        "CHANGES"), adminThirdParams(5, 5));
        metrics.addView(adminMiniMetric(session.lastActiveFrequency() + " Hz",
                        "LAST ACTIVE"), adminThirdParams(5, 0));
        addWithTop(card, metrics, 13);

        if (session.objective != null && !session.objective.trim().isEmpty()) {
            addWithTop(card, text("Objective · " + session.objective, 12,
                    TEXT_MUTED, Typeface.NORMAL), 12);
        }
        if (session.review != null && !session.review.trim().isEmpty()) {
            TextView review = text("Patient's complaint · " + session.review, 12,
                    TEXT, Typeface.BOLD);
            review.setBackground(roundRect(SURFACE_LOW, 12,
                    Color.TRANSPARENT, 0));
            review.setPadding(dp(10), dp(8), dp(10), dp(8));
            addWithTop(card, review, 10);
        }
        if (!session.frequencyMarks.isEmpty()) {
            StringBuilder timeline = new StringBuilder();
            for (int index = 0; index < session.frequencyMarks.size(); index++) {
                AdminDataStore.FrequencyMark mark = session.frequencyMarks.get(index);
                if (index > 0) {
                    timeline.append("  ·  ");
                }
                timeline.append(mark.frequency).append(" Hz @ ")
                        .append(formatClockTime(mark.timestamp));
            }
            TextView marks = text(timeline.toString(), 11,
                    PRIMARY, Typeface.BOLD);
            marks.setBackground(roundRect(SURFACE_HIGH, 12,
                    Color.TRANSPARENT, 0));
            marks.setPadding(dp(10), dp(8), dp(10), dp(8));
            addWithTop(card, marks, 10);
        }
        return card;
    }

    private LinearLayout adminMiniMetric(String value, String label) {
        LinearLayout metric = new LinearLayout(this);
        metric.setOrientation(LinearLayout.VERTICAL);
        metric.setGravity(Gravity.CENTER);
        metric.setPadding(dp(5), dp(8), dp(5), dp(8));
        metric.setBackground(roundRect(SURFACE_LOW, 12,
                Color.TRANSPARENT, 0));
        TextView valueView = text(value, 14, TEXT, Typeface.BOLD);
        valueView.setGravity(Gravity.CENTER);
        metric.addView(valueView);
        TextView labelView = text(label, 9, MUTED, Typeface.BOLD);
        labelView.setLetterSpacing(0.08f);
        labelView.setGravity(Gravity.CENTER);
        addWithTop(metric, labelView, 2);
        return metric;
    }

    private void showEnrollPatientDialog() {
        showEnrollPatientDialog(false);
    }

    private void showEnrollPatientDialog(boolean returnToPtp) {
        discardPendingPatientReports();
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(22), dp(22), dp(22), dp(20));
        panel.setBackground(roundRect(WHITE, 24, Color.TRANSPARENT, 0));

        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        ImageView headingIcon = icon(R.drawable.ic_brain_circuit, WHITE, 24);
        headingIcon.setPadding(dp(12), dp(12), dp(12), dp(12));
        headingIcon.setBackground(circleDrawable(PRIMARY));
        heading.addView(headingIcon, new LinearLayout.LayoutParams(dp(54), dp(54)));
        LinearLayout headingCopy = new LinearLayout(this);
        headingCopy.setOrientation(LinearLayout.VERTICAL);
        headingCopy.addView(text("Enroll patient", 23, TEXT, Typeface.BOLD));
        addWithTop(headingCopy, text("Create a private patient profile",
                12, TEXT_MUTED, Typeface.NORMAL), 3);
        LinearLayout.LayoutParams headingCopyParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        headingCopyParams.setMargins(dp(13), 0, 0, 0);
        heading.addView(headingCopy, headingCopyParams);
        panel.addView(heading);

        LinearLayout privacy = new LinearLayout(this);
        privacy.setGravity(Gravity.CENTER_VERTICAL);
        privacy.setPadding(dp(12), dp(9), dp(12), dp(9));
        privacy.setBackground(roundRect(PRIMARY_CONTAINER, 13,
                Color.TRANSPARENT, 0));
        privacy.addView(icon(R.drawable.ic_info, PRIMARY, 17),
                new LinearLayout.LayoutParams(dp(22), dp(22)));
        TextView privacyCopy = text("Saved locally on this device only",
                11, PRIMARY, Typeface.BOLD);
        LinearLayout.LayoutParams privacyCopyParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        privacyCopyParams.setMargins(dp(8), 0, 0, 0);
        privacy.addView(privacyCopy, privacyCopyParams);
        addWithTop(panel, privacy, 16);

        EditText name = adminField("Enter full name",
                android.text.InputType.TYPE_CLASS_TEXT |
                        android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS, false);
        EditText age = adminField("Enter age",
                android.text.InputType.TYPE_CLASS_NUMBER, false);
        EditText sex = adminField("Male, Female, or Other",
                android.text.InputType.TYPE_CLASS_TEXT |
                        android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS, false);
        EditText phone = adminField("Enter phone number",
                android.text.InputType.TYPE_CLASS_PHONE, false);
        EditText aadhaar = adminField("Enter 12-digit Aadhaar ID",
                android.text.InputType.TYPE_CLASS_NUMBER, false);
        EditText abha = adminField("Enter 14-digit ABHA ID, if available",
                android.text.InputType.TYPE_CLASS_NUMBER, false);

        addWithTop(panel, adminDialogFieldBlock("NAME", null, name), 18);
        LinearLayout demographics = new LinearLayout(this);
        demographics.setOrientation(LinearLayout.HORIZONTAL);
        demographics.addView(adminDialogFieldBlock("AGE", null, age),
                adminFormHalfParams(0, 5));
        demographics.addView(adminDialogFieldBlock("SEX", null, sex),
                adminFormHalfParams(5, 0));
        addWithTop(panel, demographics, 13);
        addWithTop(panel, adminDialogFieldBlock("NUMBER", null, phone), 13);
        addWithTop(panel, adminDialogFieldBlock("AADHAAR ID",
                "Required · stored only on this device", aadhaar), 13);
        addWithTop(panel, adminDialogFieldBlock("ABHA ID",
                "Optional", abha), 13);

        LinearLayout reports = card();
        reports.setPadding(dp(14), dp(14), dp(14), dp(14));
        reports.addView(text("Previous reports", 17, TEXT, Typeface.BOLD));
        addWithTop(reports, text(
                "Photograph a prescription or attach PDF, JPG, or PNG files.",
                11, TEXT_MUTED, Typeface.NORMAL), 4);
        pendingPatientReportsText = text("No reports attached", 12,
                TEXT_MUTED, Typeface.NORMAL);
        pendingPatientReportsText.setPadding(dp(10), dp(9), dp(10), dp(9));
        pendingPatientReportsText.setBackground(roundRect(
                SURFACE_LOW, 12, Color.TRANSPARENT, 0));
        addWithTop(reports, pendingPatientReportsText, 12);
        LinearLayout reportActions = new LinearLayout(this);
        Button camera = secondaryButton("Take photo");
        Button attach = secondaryButton("Attach file");
        camera.setOnClickListener(v -> capturePatientReport());
        attach.setOnClickListener(v -> pickPatientReports());
        reportActions.addView(camera, adminHalfParams(0, 5));
        reportActions.addView(attach, adminHalfParams(5, 0));
        addWithTop(reports, reportActions, 10);
        addWithTop(panel, reports, 14);

        LinearLayout actions = new LinearLayout(this);
        Button cancel = secondaryButton("Cancel");
        Button enroll = primaryButton("Enroll patient");
        setButtonIcon(enroll, R.drawable.ic_arrow_forward, WHITE);
        actions.addView(cancel, adminHalfParams(0, 5));
        actions.addView(enroll, adminHalfParams(5, 0));
        addWithTop(panel, actions, 18);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(panel);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(scroll)
                .create();
        final boolean[] saved = {false};
        cancel.setOnClickListener(v -> {
            discardPendingPatientReports();
            dialog.dismiss();
        });
        enroll.setOnClickListener(v -> {
            String patientName = name.getText().toString().trim();
            String ageText = age.getText().toString().trim();
            String patientSex = sex.getText().toString().trim();
            String number = phone.getText().toString().trim();
            String aadhaarDigits = aadhaar.getText().toString()
                    .replaceAll("\\D", "");
            String abhaDigits = abha.getText().toString()
                    .replaceAll("\\D", "");
            if (patientName.isEmpty()) {
                name.setError("Patient name is required");
                return;
            }
            int patientAge;
            try {
                patientAge = Integer.parseInt(ageText);
            } catch (NumberFormatException ignored) {
                age.setError("Enter a valid age");
                return;
            }
            if (patientAge < 1 || patientAge > 120) {
                age.setError("Enter an age from 1 to 120");
                return;
            }
            if (patientSex.isEmpty()) {
                sex.setError("Sex is required");
                return;
            }
            if (number.isEmpty()) {
                phone.setError("Phone number is required");
                return;
            }
            if (aadhaarDigits.length() != 12) {
                aadhaar.setError("Aadhaar ID must contain 12 digits");
                return;
            }
            if (!abhaDigits.isEmpty() && abhaDigits.length() != 14) {
                abha.setError("ABHA ID must contain 14 digits");
                return;
            }
            AdminDataStore.PatientRecord patient = adminDataStore.addPatient(
                    patientName, patientAge, patientSex, number,
                    aadhaarDigits, abhaDigits, "",
                    new ArrayList<>(pendingPatientReports));
            selectedAdminPatientId = patient.id;
            if (returnToPtp) {
                ptpPatientName = patient.name;
                ptpPatientPhone = patient.phoneNumber;
                adminPage = 3;
            }
            saved[0] = true;
            pendingPatientReports.clear();
            pendingPatientReportsText = null;
            dialog.dismiss();
            showAdminWorkspace();
        });
        dialog.setOnCancelListener(ignored -> discardPendingPatientReports());
        dialog.setOnDismissListener(ignored -> {
            if (!saved[0] && !pendingPatientReports.isEmpty()) {
                discardPendingPatientReports();
            }
            pendingPatientReportsText = null;
        });
        dialog.setOnShowListener(ignored -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(
                        new ColorDrawable(Color.TRANSPARENT));
                dialog.getWindow().setLayout(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        });
        dialog.show();
    }

    private LinearLayout adminDialogFieldBlock(String label, String helper,
                                                EditText field) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        TextView labelView = text(label, 10, PRIMARY, Typeface.BOLD);
        labelView.setLetterSpacing(0.11f);
        block.addView(labelView);
        if (helper != null && !helper.isEmpty()) {
            addWithTop(block, text(helper, 11, TEXT_MUTED, Typeface.NORMAL), 3);
        }
        addWithTop(block, field, helper == null ? 7 : 8);
        return block;
    }

    private void pickPatientReports() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/pdf", "image/jpeg", "image/png"});
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try {
            startActivityForResult(intent, PICK_PATIENT_REPORTS_REQUEST);
        } catch (ActivityNotFoundException error) {
            Toast.makeText(this, "No compatible file picker is available.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void capturePatientReport() {
        try {
            File directory = new File(getFilesDir(), "patient_reports");
            if (!directory.exists() && !directory.mkdirs()) {
                throw new IOException("Could not create report directory");
            }
            File image = new File(directory,
                    "prescription_" + UUID.randomUUID() + ".jpg");
            pendingPatientCameraPath = image.getAbsolutePath();
            Uri output = FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", image);
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, output);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            startActivityForResult(intent, CAPTURE_PATIENT_REPORT_REQUEST);
        } catch (IOException | ActivityNotFoundException error) {
            pendingPatientCameraPath = null;
            Toast.makeText(this, "The camera could not be opened.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void copyPatientReport(Uri source) {
        String mimeType = getContentResolver().getType(source);
        if (!("application/pdf".equals(mimeType) ||
                "image/jpeg".equals(mimeType) ||
                "image/png".equals(mimeType))) {
            Toast.makeText(this, "Only PDF, JPG, and PNG files are supported.",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        String displayName = "Previous report";
        try (Cursor cursor = getContentResolver().query(source,
                new String[]{OpenableColumns.DISPLAY_NAME},
                null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (column >= 0) {
                    displayName = cursor.getString(column);
                }
            }
        }
        String safeName = sanitizeFileName(displayName);
        File directory = new File(getFilesDir(), "patient_reports");
        if (!directory.exists() && !directory.mkdirs()) {
            Toast.makeText(this, "Could not create local report storage.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        File destination = new File(directory,
                UUID.randomUUID() + "_" + safeName);
        try (InputStream input = getContentResolver().openInputStream(source);
             OutputStream output = new FileOutputStream(destination)) {
            if (input == null) {
                throw new IOException("Input stream unavailable");
            }
            byte[] buffer = new byte[16_384];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            pendingPatientReports.add(new AdminDataStore.ReportAttachment(
                    displayName, destination.getAbsolutePath(), mimeType));
            updatePendingPatientReportsText();
        } catch (IOException error) {
            destination.delete();
            Toast.makeText(this, "Could not attach " + displayName + ".",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void updatePendingPatientReportsText() {
        if (pendingPatientReportsText == null) {
            return;
        }
        if (pendingPatientReports.isEmpty()) {
            pendingPatientReportsText.setText("No reports attached");
            return;
        }
        StringBuilder summary = new StringBuilder();
        for (AdminDataStore.ReportAttachment report : pendingPatientReports) {
            if (summary.length() > 0) {
                summary.append("\n");
            }
            summary.append("• ").append(report.name);
        }
        pendingPatientReportsText.setText(summary.toString());
        pendingPatientReportsText.setTextColor(TEXT);
    }

    private void discardPendingPatientReports() {
        for (AdminDataStore.ReportAttachment report : pendingPatientReports) {
            if (report.localPath != null && !report.localPath.isEmpty()) {
                new File(report.localPath).delete();
            }
        }
        pendingPatientReports.clear();
        pendingPatientCameraPath = null;
        updatePendingPatientReportsText();
    }

    private void showEditPatientDialog(AdminDataStore.PatientRecord patient) {
        LinearLayout form = adminForm();
        EditText name = adminField("Patient name",
                android.text.InputType.TYPE_CLASS_TEXT |
                        android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS, false);
        EditText age = adminField("Age",
                android.text.InputType.TYPE_CLASS_NUMBER, false);
        EditText sex = adminField("Sex",
                android.text.InputType.TYPE_CLASS_TEXT |
                        android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS, false);
        EditText phone = adminField("Phone number",
                android.text.InputType.TYPE_CLASS_PHONE, false);
        EditText aadhaar = adminField("12-digit Aadhaar ID",
                android.text.InputType.TYPE_CLASS_NUMBER, false);
        EditText abha = adminField("14-digit ABHA ID (optional)",
                android.text.InputType.TYPE_CLASS_NUMBER, false);
        name.setText(patient.name);
        age.setText(patient.age > 0 ? String.valueOf(patient.age) : "");
        sex.setText(patient.sex);
        phone.setText(patient.phoneNumber);
        aadhaar.setText(patient.aadhaarId);
        abha.setText(patient.abhaId);
        form.addView(name);
        addWithTop(form, age, 10);
        addWithTop(form, sex, 10);
        addWithTop(form, phone, 10);
        addWithTop(form, aadhaar, 10);
        addWithTop(form, abha, 10);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Edit patient")
                .setMessage("Update this locally stored patient profile.")
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save changes", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(
                AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String patientName = name.getText().toString().trim();
            String patientSex = sex.getText().toString().trim();
            String number = phone.getText().toString().trim();
            int patientAge;
            if (patientName.isEmpty()) {
                name.setError("Patient name is required");
                return;
            }
            try {
                patientAge = Integer.parseInt(age.getText().toString().trim());
            } catch (NumberFormatException ignoredError) {
                age.setError("Enter a valid age");
                return;
            }
            if (patientAge < 1 || patientAge > 120) {
                age.setError("Enter an age from 1 to 120");
                return;
            }
            if (patientSex.isEmpty()) {
                sex.setError("Sex is required");
                return;
            }
            if (number.isEmpty()) {
                phone.setError("Phone number is required");
                return;
            }
            String aadhaarDigits = aadhaar.getText().toString()
                    .replaceAll("\\D", "");
            String abhaDigits = abha.getText().toString()
                    .replaceAll("\\D", "");
            if (aadhaarDigits.length() != 12) {
                aadhaar.setError("Aadhaar ID must contain 12 digits");
                return;
            }
            if (!abhaDigits.isEmpty() && abhaDigits.length() != 14) {
                abha.setError("ABHA ID must contain 14 digits");
                return;
            }
            adminDataStore.updatePatient(patient, patientName, patientAge,
                    patientSex, number, aadhaarDigits, abhaDigits,
                    patient.review);
            dialog.dismiss();
            showAdminWorkspace();
        }));
        dialog.show();
    }

    private void showNewSessionDialog(AdminDataStore.PatientRecord patient) {
        LinearLayout form = adminForm();
        EditText number = adminField("Session number",
                android.text.InputType.TYPE_CLASS_TEXT, false);
        number.setText(String.valueOf(patient.sessions.size() + 1));
        EditText objective = adminField("Session objective",
                android.text.InputType.TYPE_CLASS_TEXT |
                        android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE, true);
        form.addView(number);
        addWithTop(form, objective, 10);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Prepare patient session")
                .setMessage(patient.name +
                        " · Next, connect the device and test a comfortable frequency.")
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Continue to setup", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(
                AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String sessionNumber = number.getText().toString().trim();
            if (sessionNumber.isEmpty()) {
                number.setError("Session number is required");
                return;
            }
            for (AdminDataStore.SessionRecord existing : patient.sessions) {
                if (sessionNumber.equalsIgnoreCase(existing.sessionNumber)) {
                    number.setError("Use a unique session number for this patient");
                    return;
                }
            }
            pendingAdminSessionNumber = sessionNumber;
            pendingAdminSessionObjective = objective.getText().toString().trim();
            activeAdminPatientId = patient.id;
            adminTestFrequency = 60;
            adminDraftFrequency = 60;
            adminSessionSetupActive = true;
            dialog.dismiss();
            showAdminWorkspace();
        }));
        dialog.show();
    }

    private void showAdminSessionSetup() {
        AdminDataStore.PatientRecord patient =
                adminDataStore.findPatient(activeAdminPatientId);
        if (patient == null || pendingAdminSessionNumber == null) {
            cancelAdminSessionSetup(false);
            showAdminWorkspace();
            return;
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.addView(adminSetupTopBar(patient),
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(86)));

        LinearLayout content = pageColumn();
        content.addView(text("Connect, test, then set", 30, TEXT, Typeface.BOLD));
        addWithTop(content, text(
                "Test the frequency without creating a record. The session timer and history begin only when you tap Set & start.",
                14, TEXT_MUTED, Typeface.NORMAL), 6);

        LinearLayout steps = tintedCard();
        steps.addView(text("1  Connect device", 13,
                connected ? SUCCESS : TEXT, Typeface.BOLD));
        addWithTop(steps, text("2  Test a comfortable frequency", 13,
                connected ? TEXT : MUTED, Typeface.BOLD), 8);
        addWithTop(steps, text("3  Set frequency and start recording", 13,
                connected ? TEXT : MUTED, Typeface.BOLD), 8);
        addWithTop(content, steps, 16);

        LinearLayout device = card();
        LinearLayout deviceRow = new LinearLayout(this);
        deviceRow.setGravity(Gravity.CENTER_VERTICAL);
        deviceRow.addView(text("NeuroVibe device", 17, TEXT, Typeface.BOLD),
                new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        deviceRow.addView(statusPill(connected ? "Connected" :
                        (scanning || connecting ? "Connecting" : "Not connected"),
                connected ? SUCCESS : (scanning || connecting ? CAUTION : ERROR),
                connected ? Color.rgb(235, 249, 239) :
                        (scanning || connecting ? Color.rgb(255, 247, 228) : ERROR_LIGHT)));
        device.addView(deviceRow);
        if (!connected) {
            Button connect = primaryButton(scanning || connecting
                    ? "Searching for NeuroVibe…" : "Connect NeuroVibe");
            connect.setEnabled(!scanning && !connecting);
            connect.setOnClickListener(v -> ensurePermissionsAndScan());
            attachPressAnimation(connect);
            addWithTop(device, connect, 14);
        }
        addWithTop(content, device, 14);

        if (connected) {
            LinearLayout control = card();
            TextView value = text(adminTestFrequency + " Hz", 26,
                    PRIMARY, Typeface.BOLD);
            value.setGravity(Gravity.CENTER);
            control.addView(value);
            CircularDialView testDial = new CircularDialView(this);
            testDial.setValue(adminTestFrequency);
            Button start = primaryButton("Set " + adminTestFrequency +
                    " Hz & start session");
            testDial.setListener(new CircularDialView.Listener() {
                @Override
                public void onValueChanged(int frequency, boolean fromUser) {
                    adminTestFrequency = frequency;
                    adminDraftFrequency = frequency;
                    value.setText(frequency + " Hz");
                    start.setText("Set " + frequency + " Hz & start session");
                }

                @Override
                public void onValueCommitted(int frequency) {
                    adminTestFrequency = frequency;
                    writeMotorValue(frequency);
                }
            });
            LinearLayout.LayoutParams dialParams = new LinearLayout.LayoutParams(
                    dp(286), dp(286));
            dialParams.gravity = Gravity.CENTER_HORIZONTAL;
            control.addView(testDial, dialParams);
            TextView hint = text(
                    "Move and release the dial to test. Test changes are not saved.",
                    12, TEXT_MUTED, Typeface.NORMAL);
            hint.setGravity(Gravity.CENTER);
            control.addView(hint);
            start.setOnClickListener(v -> startPreparedAdminSession(patient));
            attachPressAnimation(start);
            addWithTop(control, start, 14);
            addWithTop(content, control, 14);
        }

        Button cancel = secondaryButton("Cancel session setup");
        cancel.setOnClickListener(v -> {
            cancelAdminSessionSetup(true);
            showAdminWorkspace();
        });
        attachPressAnimation(cancel);
        addWithTop(content, cancel, 16);
        finishAdminPage(root, content);
    }

    private View adminSetupTopBar(AdminDataStore.PatientRecord patient) {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(14), dp(10), dp(14), dp(6));
        bar.setBackgroundColor(WHITE);
        Button back = plainButton("‹ Setup");
        back.setTextColor(PRIMARY);
        back.setOnClickListener(v -> {
            cancelAdminSessionSetup(true);
            showAdminWorkspace();
        });
        bar.addView(back, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.CENTER);
        TextView title = text("Session setup", 17, TEXT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        copy.addView(title);
        TextView subtitle = text(patient.name + " · Session " +
                        pendingAdminSessionNumber,
                11, TEXT_MUTED, Typeface.NORMAL);
        subtitle.setGravity(Gravity.CENTER);
        copy.addView(subtitle);
        bar.addView(copy, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView status = text("Preparing", 12, CAUTION, Typeface.BOLD);
        status.setGravity(Gravity.CENTER);
        bar.addView(status, new LinearLayout.LayoutParams(dp(76), dp(48)));
        return bar;
    }

    private void startPreparedAdminSession(AdminDataStore.PatientRecord patient) {
        if (!connected) {
            Toast.makeText(this, "Connect NeuroVibe before starting.",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        int frequency = Math.max(0, Math.min(MAX_VALUE, adminTestFrequency));
        if (!writeMotorValue(frequency)) {
            Toast.makeText(this, "Could not set the device frequency.",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        long startedAt = System.currentTimeMillis();
        AdminDataStore.SessionRecord session = new AdminDataStore.SessionRecord();
        session.sessionNumber = pendingAdminSessionNumber;
        session.objective = pendingAdminSessionObjective == null
                ? "" : pendingAdminSessionObjective;
        session.review = "";
        session.startedAt = startedAt;
        session.frequencyMarks.add(new AdminDataStore.FrequencyMark(
                startedAt, frequency));
        patient.sessions.add(session);
        activeAdminSession = session;
        activeAdminPatientId = patient.id;
        adminDraftFrequency = frequency;
        adminSessionSetupActive = false;
        pendingAdminSessionNumber = null;
        pendingAdminSessionObjective = null;
        adminDataStore.save();
        showAdminWorkspace();
    }

    private void cancelAdminSessionSetup(boolean stopOutput) {
        if (stopOutput && connected) {
            writeMotorValue(0);
        }
        adminSessionSetupActive = false;
        pendingAdminSessionNumber = null;
        pendingAdminSessionObjective = null;
        activeAdminPatientId = null;
        adminTestFrequency = 60;
        adminDraftFrequency = 60;
    }

    private LinearLayout adminForm() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(24), dp(8), dp(24), 0);
        return form;
    }

    private EditText adminField(String hint, int inputType, boolean multiline) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setTextSize(15);
        field.setTextColor(TEXT);
        field.setHintTextColor(MUTED);
        field.setInputType(inputType);
        field.setSingleLine(!multiline);
        field.setMinHeight(dp(multiline ? 82 : 54));
        field.setGravity(multiline ? Gravity.TOP : Gravity.CENTER_VERTICAL);
        field.setPadding(dp(14), dp(multiline ? 12 : 4), dp(14), dp(4));
        field.setBackground(roundRect(SURFACE_LOW, 14, OUTLINE, 1));
        return field;
    }

    private void showActiveAdminSession() {
        AdminDataStore.PatientRecord patient =
                adminDataStore.findPatient(activeAdminPatientId);
        if (patient == null || activeAdminSession == null) {
            activeAdminSession = null;
            activeAdminPatientId = null;
            showAdminWorkspace();
            return;
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.addView(adminTopBar("Session in progress",
                        patient.name + " · Session " + activeAdminSession.sessionNumber,
                        true),
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(86)));

        LinearLayout content = pageColumn();
        LinearLayout liveHeader = new LinearLayout(this);
        liveHeader.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout liveCopy = new LinearLayout(this);
        liveCopy.setOrientation(LinearLayout.VERTICAL);
        TextView live = text("LIVE SESSION", 11, ERROR, Typeface.BOLD);
        live.setLetterSpacing(0.13f);
        liveCopy.addView(live);
        addWithTop(liveCopy, text(patient.name, 27, TEXT, Typeface.BOLD), 4);
        liveHeader.addView(liveCopy, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        adminDurationText = text(formatDuration(activeAdminSession.durationMillis()),
                19, PRIMARY, Typeface.BOLD);
        adminDurationText.setGravity(Gravity.CENTER);
        adminDurationText.setPadding(dp(12), dp(8), dp(12), dp(8));
        adminDurationText.setBackground(roundRect(PRIMARY_CONTAINER, 99,
                Color.TRANSPARENT, 0));
        liveHeader.addView(adminDurationText);
        content.addView(liveHeader);

        TextView start = text("Started " +
                        formatDateTime(activeAdminSession.startedAt) +
                        " · Every committed frequency is recorded",
                12, TEXT_MUTED, Typeface.NORMAL);
        addWithTop(content, start, 7);

        LinearLayout control = card();
        LinearLayout controlHeader = new LinearLayout(this);
        controlHeader.setGravity(Gravity.CENTER_VERTICAL);
        controlHeader.addView(sectionEyebrow("SESSION FREQUENCY"),
                new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        controlHeader.addView(statusPill(connected ? "Device connected" : "Disconnected",
                connected ? SUCCESS : ERROR,
                connected ? Color.rgb(235, 249, 239) : ERROR_LIGHT));
        control.addView(controlHeader);

        adminDraftFrequency = activeAdminSession.finalFrequency();
        TextView draftValue = text(adminDraftFrequency + " Hz selected",
                18, PRIMARY, Typeface.BOLD);
        draftValue.setGravity(Gravity.CENTER);
        addWithTop(control, draftValue, 10);
        Button applyFrequency = primaryButton("Set " + adminDraftFrequency + " Hz");

        CircularDialView adminDial = new CircularDialView(this);
        adminDial.setValue(adminDraftFrequency);
        adminDial.setEnabled(connected);
        adminDial.setListener(new CircularDialView.Listener() {
            @Override
            public void onValueChanged(int value, boolean fromUser) {
                currentMotorValue = value;
                adminDraftFrequency = value;
                draftValue.setText(value + " Hz selected");
                applyFrequency.setText("Set " + value + " Hz");
            }

            @Override
            public void onValueCommitted(int value) {
                adminDraftFrequency = value;
                writeMotorValue(value);
            }
        });
        LinearLayout.LayoutParams dialParams = new LinearLayout.LayoutParams(
                dp(286), dp(286));
        dialParams.gravity = Gravity.CENTER_HORIZONTAL;
        dialParams.topMargin = dp(8);
        control.addView(adminDial, dialParams);
        TextView dialHint = text(
                "Move and release to test. Tap Set to timestamp and save the new frequency.",
                12, TEXT_MUTED, Typeface.NORMAL);
        dialHint.setGravity(Gravity.CENTER);
        control.addView(dialHint);
        applyFrequency.setEnabled(connected);
        applyFrequency.setOnClickListener(v ->
                recordAdminFrequency(adminDraftFrequency));
        attachPressAnimation(applyFrequency);
        addWithTop(control, applyFrequency, 14);
        addWithTop(content, control, 16);

        addWithTop(content, sectionEyebrow("FREQUENCY TIMELINE"), 22);
        LinearLayout timeline = card();
        timeline.setPadding(0, dp(4), 0, dp(4));
        for (int index = 0;
             index < activeAdminSession.frequencyMarks.size(); index++) {
            AdminDataStore.FrequencyMark mark =
                    activeAdminSession.frequencyMarks.get(index);
            timeline.addView(adminFrequencyRow(index + 1, mark));
            if (index < activeAdminSession.frequencyMarks.size() - 1) {
                timeline.addView(hairline());
            }
        }
        addWithTop(content, timeline, 10);

        Button end = primaryButton("End and save session");
        end.setBackground(rippleBackground(ERROR, 16));
        setButtonIcon(end, R.drawable.ic_stop, WHITE);
        end.setOnClickListener(v -> showEndAdminSessionDialog());
        attachPressAnimation(end);
        addWithTop(content, end, 18);
        TextView safety = text(
                "Ending the session sends 0 Hz, records the end time, and saves the complete timeline.",
                11, TEXT_MUTED, Typeface.NORMAL);
        safety.setGravity(Gravity.CENTER);
        safety.setPadding(dp(12), dp(12), dp(12), dp(4));
        content.addView(safety);

        ScrollView scroll = scroll(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        handler.post(adminDurationTick);
        springPageIn(scroll);
    }

    private LinearLayout adminFrequencyRow(int index,
                                           AdminDataStore.FrequencyMark mark) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(12), dp(16), dp(12));
        TextView number = text(String.valueOf(index), 12, WHITE, Typeface.BOLD);
        number.setGravity(Gravity.CENTER);
        number.setBackground(circleDrawable(PRIMARY));
        row.addView(number, new LinearLayout.LayoutParams(dp(30), dp(30)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text(mark.frequency + " Hz", 16, TEXT, Typeface.BOLD));
        addWithTop(copy, text("At " + formatClockTime(mark.timestamp) +
                        " · " + formatDuration(Math.max(0,
                        mark.timestamp - activeAdminSession.startedAt)) + " elapsed",
                11, TEXT_MUTED, Typeface.NORMAL), 2);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        copyParams.setMargins(dp(11), 0, 0, 0);
        row.addView(copy, copyParams);
        return row;
    }

    private void recordAdminFrequency(int value) {
        if (activeAdminSession == null || !connected) {
            Toast.makeText(this, "NeuroVibe is not connected.",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        int constrained = Math.max(0, Math.min(MAX_VALUE, value));
        if (activeAdminSession.finalFrequency() == constrained) {
            return;
        }
        if (!writeMotorValue(constrained)) {
            return;
        }
        activeAdminSession.frequencyMarks.add(
                new AdminDataStore.FrequencyMark(
                        System.currentTimeMillis(), constrained));
        adminDataStore.save();
        showAdminWorkspace();
    }

    private void showEndAdminSessionDialog() {
        AdminDataStore.PatientRecord patient =
                adminDataStore.findPatient(activeAdminPatientId);
        if (patient == null || activeAdminSession == null) {
            return;
        }
        EditText review = adminField("Patient's complaint after the session",
                android.text.InputType.TYPE_CLASS_TEXT |
                        android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE, true);
        review.setText(activeAdminSession.review);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("End session " + activeAdminSession.sessionNumber + "?")
                .setMessage("Record the patient's complaint before ending. This sends 0 Hz and saves the final duration for " +
                        patient.name + ".")
                .setView(review)
                .setNegativeButton("Continue session", null)
                .setPositiveButton("End and save", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            Button endButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            endButton.setTextColor(ERROR);
            endButton.setOnClickListener(v -> {
                String patientReview = review.getText().toString().trim();
                if (patientReview.isEmpty()) {
                    review.setError("Patient's complaint is required to end the session");
                    review.requestFocus();
                    return;
                }
                dialog.dismiss();
                finishActiveAdminSession(patientReview, true);
            });
        });
        dialog.show();
    }

    private void finishActiveAdminSession(String review, boolean render) {
        if (activeAdminSession == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (activeAdminSession.finalFrequency() != 0) {
            activeAdminSession.frequencyMarks.add(
                    new AdminDataStore.FrequencyMark(now, 0));
        }
        activeAdminSession.review = review;
        activeAdminSession.endedAt = now;
        currentMotorValue = 0;
        writeMotorValue(0);
        adminDataStore.save();
        activeAdminSession = null;
        activeAdminPatientId = null;
        handler.removeCallbacks(adminDurationTick);
        adminDurationText = null;
        if (render) {
            showAdminWorkspace();
        }
    }

    private void exportPatientHistory(AdminDataStore.PatientRecord patient) {
        try {
            pendingExportBytes = PatientXlsxExporter.create(patient);
            pendingExportName = sanitizeFileName(patient.name) +
                    "_NeuroSense_History.xlsx";
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            intent.putExtra(Intent.EXTRA_TITLE, pendingExportName);
            startActivityForResult(intent, EXPORT_XLSX_REQUEST);
        } catch (IOException error) {
            Toast.makeText(this, "Could not prepare the Excel history.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void exportPtpActivity() {
        List<PtpActivityStore.ActivityRecord> records =
                ptpActivityStore.recordsNewestFirst();
        if (records.isEmpty()) {
            Toast.makeText(this, "No PTP activity is available to export.",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            pendingExportBytes = PatientXlsxExporter.createPtpActivity(records);
            pendingExportName = "NeuroSense_PTP_Activity_" +
                    new SimpleDateFormat("yyyy-MM-dd", Locale.US)
                            .format(new Date()) + ".xlsx";
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            intent.putExtra(Intent.EXTRA_TITLE, pendingExportName);
            startActivityForResult(intent, EXPORT_XLSX_REQUEST);
        } catch (IOException error) {
            Toast.makeText(this, "Could not prepare the PTP activity workbook.",
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_PATIENT_REPORTS_REQUEST) {
            if (resultCode == RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    ClipData files = data.getClipData();
                    for (int index = 0; index < files.getItemCount(); index++) {
                        copyPatientReport(files.getItemAt(index).getUri());
                    }
                } else if (data.getData() != null) {
                    copyPatientReport(data.getData());
                }
            }
            return;
        }
        if (requestCode == CAPTURE_PATIENT_REPORT_REQUEST) {
            if (resultCode == RESULT_OK && pendingPatientCameraPath != null) {
                File image = new File(pendingPatientCameraPath);
                if (image.exists() && image.length() > 0) {
                    pendingPatientReports.add(
                            new AdminDataStore.ReportAttachment(
                                    "Prescription photo " +
                                            new SimpleDateFormat(
                                                    "dd MMM yyyy hh-mm a", Locale.US)
                                                    .format(new Date()) + ".jpg",
                                    image.getAbsolutePath(), "image/jpeg"));
                    updatePendingPatientReportsText();
                }
            } else if (pendingPatientCameraPath != null) {
                new File(pendingPatientCameraPath).delete();
            }
            pendingPatientCameraPath = null;
            return;
        }
        if (requestCode != EXPORT_XLSX_REQUEST || resultCode != RESULT_OK ||
                data == null || data.getData() == null ||
                pendingExportBytes == null) {
            return;
        }
        Uri destination = data.getData();
        try (OutputStream output =
                     getContentResolver().openOutputStream(destination)) {
            if (output == null) {
                throw new IOException("Output stream unavailable");
            }
            output.write(pendingExportBytes);
            output.flush();
            Toast.makeText(this,
                    "Excel file saved: " + pendingExportName,
                    Toast.LENGTH_LONG).show();
        } catch (IOException error) {
            Toast.makeText(this, "Could not save the Excel file.",
                    Toast.LENGTH_LONG).show();
        } finally {
            pendingExportBytes = null;
            pendingExportName = null;
        }
    }

    private void closeAdminWorkspace() {
        if (adminSessionSetupActive) {
            cancelAdminSessionSetup(true);
        }
        handler.removeCallbacks(adminDurationTick);
        adminDurationText = null;
        adminWorkspaceVisible = false;
        adminUnlocked = false;
        adminPage = 0;
        buildAppShell();
        showPage(PAGE_SETTINGS);
    }

    private String sanitizeFileName(String name) {
        String sanitized = name == null ? "Patient"
                : name.trim().replaceAll("[^A-Za-z0-9._-]+", "_");
        return sanitized.isEmpty() ? "Patient" : sanitized;
    }

    private String formatDateTime(long timestamp) {
        if (timestamp <= 0) {
            return "Not recorded";
        }
        return new SimpleDateFormat("dd MMM yyyy · hh:mm a", Locale.US)
                .format(new Date(timestamp));
    }

    private String formatClockTime(long timestamp) {
        return new SimpleDateFormat("hh:mm:ss a", Locale.US)
                .format(new Date(timestamp));
    }

    private String formatDuration(long millis) {
        long seconds = Math.max(0L, millis / 1_000L);
        long hours = seconds / 3_600L;
        long minutes = (seconds % 3_600L) / 60L;
        long remainder = seconds % 60L;
        return String.format(Locale.US, "%02d:%02d:%02d",
                hours, minutes, remainder);
    }

    private void showDiagnostics() {
        String status = connected ? "Connected"
                : connecting ? "Connecting"
                : scanning ? "Scanning"
                : communicationError ? "Communication error" : "Disconnected";
        String diagnostics = "NeuroSense " + getAppVersion() + "\n" +
                "Device: NeuroVibe\n" +
                "Connection: " + status + "\n" +
                "Current level: " + currentMotorValue + " Hz\n" +
                "Storage: Local only";
        new AlertDialog.Builder(this)
                .setTitle("App status")
                .setMessage(diagnostics)
                .setNegativeButton("Done", null)
                .setPositiveButton("Copy", (dialog, which) -> {
                    ClipboardManager clipboard = (ClipboardManager)
                            getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(ClipData.newPlainText(
                                "NeuroSense diagnostics", diagnostics));
                        Toast.makeText(this, "Status copied.",
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private void selectPreset(String name, int value) {
        selectedPresetName = name;
        selectedPresetValue = value;
        renderCurrentPage();
    }

    private String getAppVersion() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException ignored) {
            return "1.0";
        }
    }

    private void startSelectedPreset() {
        if (!connected || selectedPresetValue < 0) {
            Toast.makeText(this, "Connect NeuroVibe first.",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        commitMotorValue(selectedPresetValue);
        showPage(PAGE_CONTROL);
    }

    private void showCustomPresetEditor() {
        int saved = preferences.getInt("custom_preset", 120);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        TextView value = text(saved + " Hz", 26, PRIMARY, Typeface.BOLD);
        value.setGravity(Gravity.CENTER);
        SeekBar slider = new SeekBar(this);
        slider.setMax(MAX_VALUE);
        slider.setProgress(saved);
        applyIosSliderStyle(slider, true);
        slider.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(
                    SeekBar seekBar, int progress, boolean fromUser) {
                value.setText(progress + " Hz");
            }
        });
        box.addView(value);
        box.addView(slider);

        new AlertDialog.Builder(this)
                .setTitle("Custom Preset")
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save locally", (dialog, which) -> {
                    preferences.edit().putInt(
                            "custom_preset", slider.getProgress()).apply();
                    renderCurrentPage();
                })
                .show();
    }

    private void showSettings() {
        new AlertDialog.Builder(this)
                .setTitle("NeuroSense")
                .setMessage("Private local NeuroVibe companion\n\n" +
                        "Local admin records · No cloud · No remote database\n" +
                        "Designed for NeuroVibe")
                .setPositiveButton("Done", null)
                .show();
    }

    private void showConnectivityHelp() {
        new AlertDialog.Builder(this)
                .setTitle("Connectivity checklist")
                .setMessage("1. Power on NeuroVibe.\n" +
                        "2. Confirm the device is ready.\n" +
                        "3. Enable Nearby Devices access.\n" +
                        "4. Keep the phone close to the device.\n" +
                        "5. Connect inside NeuroSense—not system settings.")
                .setPositiveButton("Done", null)
                .show();
    }

    private void showHardwareHelp() {
        new AlertDialog.Builder(this)
                .setTitle("Device care")
                .setMessage("• Keep NeuroVibe dry.\n" +
                        "• Handle the device gently.\n" +
                        "• Inspect the device before each use.\n" +
                        "• Wipe surfaces with a dry, lint-free cloth.\n" +
                        "• Stop use if any part appears damaged.")
                .setPositiveButton("Done", null)
                .show();
    }

    private void emergencyStop() {
        currentMotorValue = 0;
        writeMotorValue(0);
        if (motorSlider != null) {
            motorSlider.setValue(0);
        }
        if (motorDial != null) {
            motorDial.setValue(0);
        }
        vibrateStop();
        Toast.makeText(this, "NeuroVibe stopped.", Toast.LENGTH_SHORT).show();
        renderCurrentPage();
    }

    private void vibrateStop() {
        Vibrator vibrator = getSystemService(Vibrator.class);
        if (vibrator == null || !vibrator.hasVibrator()) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(
                    80, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            vibrator.vibrate(80);
        }
    }

    private void ensurePermissionsAndScan() {
        if (bluetoothAdapter == null) {
            return;
        }
        if (!bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "Turn on nearby device access, then try again.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        String[] permissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions = new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
            };
        } else {
            permissions = new String[]{
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
            };
        }

        boolean granted = true;
        for (String permission : permissions) {
            granted &= checkSelfPermission(permission)
                    == PackageManager.PERMISSION_GRANTED;
        }
        if (granted) {
            startScan();
        } else {
            showBluetoothPermissionRationale(permissions);
        }
    }

    private void showBluetoothPermissionRationale(String[] permissions) {
        String message = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ? "NeuroSense needs Nearby Devices permission only to find and connect directly to NeuroVibe. No location, account, cloud, or internet data is used."
                : "Android requires Location permission to find nearby devices on this version. NeuroSense uses it only to find NeuroVibe; no location data is stored or transmitted.";
        new AlertDialog.Builder(this)
                .setTitle("Nearby device access")
                .setMessage(message)
                .setNegativeButton("Not now", null)
                .setPositiveButton("Continue", (dialog, which) ->
                        requestPermissions(permissions, PERMISSION_REQUEST))
                .show();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != PERMISSION_REQUEST) {
            return;
        }
        boolean granted = grantResults.length > 0;
        for (int result : grantResults) {
            granted &= result == PackageManager.PERMISSION_GRANTED;
        }
        if (granted) {
            startScan();
        } else {
            Toast.makeText(this,
                    "Nearby Devices permission is required to find NeuroVibe.",
                    Toast.LENGTH_LONG).show();
        }
    }

    @SuppressLint("MissingPermission")
    private void startScan() {
        scanner = bluetoothAdapter.getBluetoothLeScanner();
        if (scanner == null) {
            Toast.makeText(this, "Nearby connection is unavailable.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        ScanFilter filter = new ScanFilter.Builder()
                .setServiceUuid(new ParcelUuid(SERVICE_UUID)).build();
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
        deviceNotFound = false;
        communicationError = false;
        connecting = false;
        scanning = true;
        updateTopConnection();
        renderCurrentPage();
        scanner.startScan(Collections.singletonList(filter), settings,
                scanCallback);
        handler.postDelayed(scanTimeout, SCAN_TIMEOUT_MS);
    }

    @SuppressLint("MissingPermission")
    private void stopScan() {
        handler.removeCallbacks(scanTimeout);
        if (scanning && scanner != null) {
            scanner.stopScan(scanCallback);
        }
        scanning = false;
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        @SuppressLint("MissingPermission")
        public void onScanResult(int callbackType, ScanResult result) {
            lastRssi = result.getRssi();
            BluetoothDevice device = result.getDevice();
            stopScan();
            connecting = true;
            runOnUiThread(() -> {
                updateTopConnection();
                renderCurrentPage();
            });
            bluetoothGatt = device.connectGatt(
                    MainActivity.this, false, gattCallback,
                    BluetoothDevice.TRANSPORT_LE);
        }

        @Override
        public void onScanFailed(int errorCode) {
            runOnUiThread(() -> {
                stopScan();
                deviceNotFound = true;
                Toast.makeText(MainActivity.this,
                        "Device search failed (" + errorCode + ").",
                        Toast.LENGTH_LONG).show();
                renderCurrentPage();
            });
        }
    };

    private final BluetoothGattCallback gattCallback =
            new BluetoothGattCallback() {
                @Override
                @SuppressLint("MissingPermission")
                public void onConnectionStateChange(
                        BluetoothGatt gatt, int status, int newState) {
                    if (status == BluetoothGatt.GATT_SUCCESS &&
                            newState == BluetoothProfile.STATE_CONNECTED) {
                        gatt.discoverServices();
                    } else if (newState ==
                            BluetoothProfile.STATE_DISCONNECTED) {
                        gatt.close();
                        if (bluetoothGatt == gatt) {
                            bluetoothGatt = null;
                        }
                        motorCharacteristic = null;
                        connecting = false;
                        connected = false;
                        currentMotorValue = 0;
                        lastSentValue = -1;
                        ptpActivityStore.finishConnection(
                                System.currentTimeMillis());
                        runOnUiThread(() -> {
                            if (activeAdminSession != null) {
                                String review = activeAdminSession.review == null
                                        ? "" : activeAdminSession.review;
                                finishActiveAdminSession((review +
                                        "\nEnded automatically after the device disconnected.")
                                        .trim(), false);
                            }
                            updateTopConnection();
                            renderCurrentPage();
                            Toast.makeText(MainActivity.this,
                                    "NeuroVibe disconnected and stopped safely.",
                                    Toast.LENGTH_LONG).show();
                        });
                    } else if (status != BluetoothGatt.GATT_SUCCESS) {
                        gatt.disconnect();
                    }
                }

                @Override
                public void onServicesDiscovered(
                        BluetoothGatt gatt, int status) {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        runOnUiThread(() -> {
                            Toast.makeText(MainActivity.this,
                                    "Could not read NeuroVibe services.",
                                    Toast.LENGTH_LONG).show();
                            disconnectDevice();
                        });
                        return;
                    }
                    BluetoothGattService service = gatt.getService(SERVICE_UUID);
                    BluetoothGattCharacteristic characteristic = service == null
                            ? null : service.getCharacteristic(
                            MOTOR_COMMAND_UUID);
                    if (characteristic == null) {
                        runOnUiThread(() -> {
                            Toast.makeText(MainActivity.this,
                                    "NeuroVibe could not be verified.",
                                    Toast.LENGTH_LONG).show();
                            disconnectDevice();
                        });
                        return;
                    }
                    motorCharacteristic = characteristic;
                    connecting = false;
                    connected = true;
                    currentMotorValue = 0;
                    lastSentValue = -1;
                    if (ptpEnabled) {
                        ptpActivityStore.startConnection(
                                ptpPatientName, ptpPatientPhone,
                                System.currentTimeMillis());
                    }
                    writeMotorValue(0);
                    runOnUiThread(() -> {
                        updateTopConnection();
                        renderCurrentPage();
                    });
                }
            };

    @SuppressLint("MissingPermission")
    private boolean writeMotorValue(int value) {
        if (!connected || bluetoothGatt == null ||
                motorCharacteristic == null) {
            return false;
        }
        int constrained = Math.max(0, Math.min(MAX_VALUE, value));
        if (!isPtpApprovedFrequency(constrained)) {
            return false;
        }
        if (constrained == lastSentValue) {
            return true;
        }
        currentMotorValue = constrained;
        byte[] command = new byte[]{(byte) currentMotorValue};
        boolean accepted;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            accepted = bluetoothGatt.writeCharacteristic(
                    motorCharacteristic, command,
                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
                    == android.bluetooth.BluetoothStatusCodes.SUCCESS;
        } else {
            motorCharacteristic.setWriteType(
                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
            motorCharacteristic.setValue(command);
            accepted = bluetoothGatt.writeCharacteristic(motorCharacteristic);
        }
        if (accepted) {
            lastSentValue = currentMotorValue;
            if (ptpEnabled) {
                long activityTimestamp = System.currentTimeMillis();
                if (!ptpActivityStore.hasActiveRecord()) {
                    ptpActivityStore.startConnection(
                            ptpPatientName, ptpPatientPhone,
                            activityTimestamp);
                }
                ptpActivityStore.recordFrequency(
                        currentMotorValue, activityTimestamp);
            }
        } else {
            handleCommunicationFailure();
        }
        return accepted;
    }

    private void commitMotorValue(int value) {
        if (!isPtpApprovedFrequency(value)) {
            Toast.makeText(this,
                    "Patient mode allows only Low, Medium, or High.",
                    Toast.LENGTH_SHORT).show();
            currentMotorValue = Math.max(0, lastSentValue);
            updateControlReadout(currentMotorValue);
            return;
        }
        if (writeMotorValue(value) && pageContainer != null) {
            pageContainer.performHapticFeedback(
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                            ? android.view.HapticFeedbackConstants.CONFIRM
                            : android.view.HapticFeedbackConstants.CLOCK_TICK);
        }
    }

    private boolean isPtpApprovedFrequency(int value) {
        return !ptpEnabled || value == 0 ||
                value == ptpLowFrequency ||
                value == ptpMediumFrequency ||
                value == ptpHighFrequency;
    }

    @SuppressLint("MissingPermission")
    private void handleCommunicationFailure() {
        communicationError = true;
        currentMotorValue = 0;
        lastSentValue = -1;
        ptpActivityStore.finishConnection(System.currentTimeMillis());
        if (bluetoothGatt != null) {
            bluetoothGatt.disconnect();
        }
        runOnUiThread(() -> {
            if (activeAdminSession != null) {
                String review = activeAdminSession.review == null
                        ? "" : activeAdminSession.review;
                finishActiveAdminSession((review +
                        "\nEnded automatically after a communication failure.").trim(),
                        false);
            }
            Toast.makeText(this,
                    "Communication failed. NeuroVibe was stopped safely.",
                    Toast.LENGTH_LONG).show();
            updateTopConnection();
            renderCurrentPage();
        });
    }

    @SuppressLint("MissingPermission")
    private void disconnectDevice() {
        stopScan();
        currentMotorValue = 0;
        if (bluetoothGatt != null) {
            writeMotorValue(0);
            ptpActivityStore.finishConnection(System.currentTimeMillis());
            bluetoothGatt.disconnect();
        } else {
            ptpActivityStore.finishConnection(System.currentTimeMillis());
            connecting = false;
            connected = false;
            updateTopConnection();
            renderCurrentPage();
        }
    }

    private void updateTopConnection() {
        if (topConnectionText == null) {
            return;
        }
        String label = connected ? "CONNECTED"
                : connecting ? "CONNECTING"
                : scanning ? "SEARCHING"
                : communicationError ? "ERROR" : "DISCONNECTED";
        int color = connected ? SUCCESS
                : communicationError ? ERROR
                : (scanning || connecting) ? PRIMARY : TEXT_MUTED;
        topConnectionText.setText(label);
        topConnectionText.setTextColor(color);
        if (topStatusIcon != null) {
            topStatusIcon.setImageTintList(ColorStateList.valueOf(color));
        }
        if (topStatusPill != null) {
            topStatusPill.setBackground(roundRect(
                    connected ? Color.rgb(237, 249, 245)
                            : communicationError ? ERROR_LIGHT : SURFACE_LOW,
                    99, color, 1));
        }
    }

    private ScrollView scroll(LinearLayout content) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, 0, 0, dp(24));
        scroll.addView(content);
        return scroll;
    }

    private LinearLayout pageColumn() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(12), dp(20), dp(28));
        return content;
    }

    private TextView sectionEyebrow(String label) {
        TextView view = text(label, 11, PRIMARY, Typeface.BOLD);
        view.setLetterSpacing(0.14f);
        return view;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(roundRect(WHITE, 22, OUTLINE, 1));
        card.setElevation(dp(2));
        return card;
    }

    private LinearLayout tintedCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(roundRect(SURFACE_LOW, 20, OUTLINE, 1));
        return card;
    }

    private LinearLayout smallInfoCard(
            int iconResource, String title, String value) {
        LinearLayout card = card();
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(12), dp(15), dp(12), dp(15));
        ImageView iconView = icon(iconResource, PRIMARY, 20);
        card.addView(iconView, new LinearLayout.LayoutParams(dp(28), dp(28)));
        TextView titleText = text(title, 11, TEXT_MUTED, Typeface.BOLD);
        titleText.setGravity(Gravity.CENTER);
        titleText.setPadding(0, dp(5), 0, dp(2));
        card.addView(titleText);
        TextView valueText = text(value, 15, TEXT, Typeface.BOLD);
        valueText.setGravity(Gravity.CENTER);
        card.addView(valueText);
        return card;
    }

    private LinearLayout statusPill(String label, int color, int background) {
        LinearLayout pill = new LinearLayout(this);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(dp(10), dp(6), dp(11), dp(6));
        pill.setBackground(roundRect(background, 99, Color.TRANSPARENT, 0));
        View dot = new View(this);
        dot.setBackground(circleDrawable(color));
        pill.addView(dot, new LinearLayout.LayoutParams(dp(8), dp(8)));
        TextView labelView = text(label, 12, color, Typeface.BOLD);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.setMargins(dp(6), 0, 0, 0);
        pill.addView(labelView, labelParams);
        return pill;
    }

    private LinearLayout insetActionRow(int iconResource, String label,
                                        String detail, String action) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(14), dp(14), dp(14));
        row.setClickable(true);
        row.setFocusable(true);
        row.setBackground(rippleBackground(Color.TRANSPARENT, 18));
        ImageView rowIcon = icon(iconResource, PRIMARY, 20);
        rowIcon.setPadding(dp(8), dp(8), dp(8), dp(8));
        rowIcon.setBackground(roundRect(PRIMARY_CONTAINER, 10,
                Color.TRANSPARENT, 0));
        row.addView(rowIcon, new LinearLayout.LayoutParams(dp(38), dp(38)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text(label, 14, TEXT_MUTED, Typeface.NORMAL));
        copy.addView(text(detail, 16, TEXT, Typeface.BOLD));
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        copyParams.setMargins(dp(12), 0, dp(8), 0);
        row.addView(copy, copyParams);
        TextView actionView = text(action + "  ›", 14, PRIMARY, Typeface.BOLD);
        actionView.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        row.addView(actionView);
        return row;
    }

    private LinearLayout metricCard(String label, String value, String detail) {
        LinearLayout metric = new LinearLayout(this);
        metric.setOrientation(LinearLayout.VERTICAL);
        metric.setPadding(dp(15), dp(14), dp(15), dp(14));
        metric.setBackground(roundRect(WHITE, 18, OUTLINE, 1));
        TextView labelView = text(label, 10, TEXT_MUTED, Typeface.BOLD);
        labelView.setLetterSpacing(0.1f);
        metric.addView(labelView);
        addWithTop(metric, text(value, 17, TEXT, Typeface.BOLD), 7);
        addWithTop(metric, text(detail, 11, SUCCESS, Typeface.BOLD), 3);
        return metric;
    }

    private View hairline() {
        View line = new View(this);
        line.setBackgroundColor(OUTLINE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        params.setMargins(dp(66), 0, 0, 0);
        line.setLayoutParams(params);
        return line;
    }

    private Button primaryButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(16);
        button.setTextColor(WHITE);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setMinHeight(dp(54));
        button.setGravity(Gravity.CENTER);
        button.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
        button.setPadding(dp(18), 0, dp(18), 0);
        button.setBackground(rippleBackground(PRIMARY, 16));
        return button;
    }

    private Button secondaryButton(String label) {
        Button button = primaryButton(label);
        button.setTextColor(PRIMARY);
        button.setBackground(rippleBackground(PRIMARY_CONTAINER, 16));
        return button;
    }

    private Button plainButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(15);
        button.setTextColor(PRIMARY);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setBackground(rippleBackground(Color.TRANSPARENT, 14));
        return button;
    }

    private Button roundIconButton(String label, boolean enabled) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(22);
        button.setTextColor(PRIMARY);
        button.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
        button.setAllCaps(false);
        button.setPadding(0, 0, 0, dp(2));
        button.setBackground(rippleBackground(WHITE, 99));
        button.setElevation(dp(3));
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1f : 0.38f);
        return button;
    }

    private Button segmentButton(String label, boolean selected) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(13);
        button.setTextColor(selected ? PRIMARY : TEXT_MUTED);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setBackground(rippleBackground(
                selected ? WHITE : Color.TRANSPARENT, 8));
        button.setElevation(selected ? dp(1) : 0);
        button.setSelected(selected);
        return button;
    }

    private Button outlineButton(String label, int color) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(17);
        button.setTextColor(color);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setMinHeight(dp(56));
        button.setBackground(roundRect(WHITE, 18, color, 2));
        return button;
    }

    private Button smallOutlineButton(String label) {
        Button button = outlineButton(label, PRIMARY);
        button.setTextSize(13);
        button.setMinHeight(dp(42));
        button.setPadding(dp(16), 0, dp(16), 0);
        return button;
    }

    private Button compactButton(String label, boolean enabled) {
        Button button = outlineButton(label, PRIMARY);
        button.setTextSize(14);
        button.setMinHeight(dp(44));
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1f : 0.42f);
        return button;
    }

    private Button smallButton(String label, int color) {
        Button button = primaryButton(label);
        button.setTextSize(13);
        button.setBackground(roundRect(color, 16, Color.TRANSPARENT, 0));
        button.setPadding(dp(8), 0, dp(8), 0);
        return button;
    }

    private TextView text(
            String value, float size, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans-serif", style));
        view.setLineSpacing(0, 1.08f);
        return view;
    }

    private ImageView icon(int resource, int tint, int contentSizeDp) {
        ImageView view = new ImageView(this);
        view.setImageResource(resource);
        view.setImageTintList(ColorStateList.valueOf(tint));
        view.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        view.setAdjustViewBounds(true);
        view.setMinimumWidth(dp(contentSizeDp));
        view.setMinimumHeight(dp(contentSizeDp));
        return view;
    }

    private void setButtonIcon(Button button, int resource, int tint) {
        Drawable drawable = getDrawable(resource);
        if (drawable == null) {
            return;
        }
        drawable = drawable.mutate();
        drawable.setBounds(0, 0, dp(21), dp(21));
        drawable.setTint(tint);
        String label = button.getText().toString();
        SpannableStringBuilder centeredContent = new SpannableStringBuilder(
                "\uFFFC  " + label);
        centeredContent.setSpan(new ImageSpan(drawable, ImageSpan.ALIGN_CENTER),
                0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        button.setGravity(Gravity.CENTER);
        button.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        button.setCompoundDrawablesRelative(null, null, null, null);
        button.setText(centeredContent);
        button.setContentDescription(label);
    }

    private void applyIosSliderStyle(SeekBar slider, boolean showThumb) {
        slider.setProgressDrawable(getDrawable(
                R.drawable.ios_slider_progress));
        slider.setSplitTrack(false);
        slider.setPadding(dp(2), dp(5), dp(2), dp(5));
        slider.setMinimumHeight(dp(36));
        if (showThumb) {
            slider.setThumb(getDrawable(R.drawable.ios_slider_thumb));
            slider.setThumbOffset(0);
        } else {
            slider.setThumb(null);
        }
    }

    private GradientDrawable roundRect(
            int fill, float radiusDp, int stroke, int strokeDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0 && stroke != Color.TRANSPARENT) {
            drawable.setStroke(dp(strokeDp), stroke);
        }
        return drawable;
    }

    private RippleDrawable rippleBackground(int fill, float radiusDp) {
        GradientDrawable content = roundRect(fill, radiusDp,
                Color.TRANSPARENT, 0);
        GradientDrawable mask = roundRect(Color.WHITE, radiusDp,
                Color.TRANSPARENT, 0);
        return new RippleDrawable(
                ColorStateList.valueOf(Color.argb(34, 103, 85, 217)),
                content, mask);
    }

    private GradientDrawable circleDrawable(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        return drawable;
    }

    private void addWithTop(
            LinearLayout parent, View child, int marginTopDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(marginTopDp);
        parent.addView(child, params);
    }

    private void springPageIn(View view) {
        view.setAlpha(0f);
        view.setTranslationY(dp(10));
        SpringForce movement = new SpringForce(0f)
                .setDampingRatio(0.82f)
                .setStiffness(520f);
        SpringAnimation translation = new SpringAnimation(
                view, DynamicAnimation.TRANSLATION_Y);
        translation.setSpring(movement);
        translation.start();

        SpringForce fadeForce = new SpringForce(1f)
                .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY)
                .setStiffness(700f);
        SpringAnimation fade = new SpringAnimation(view, DynamicAnimation.ALPHA);
        fade.setSpring(fadeForce);
        fade.start();
    }

    private void springScale(View view, float target) {
        SpringForce forceX = new SpringForce(target)
                .setDampingRatio(0.72f)
                .setStiffness(650f);
        SpringForce forceY = new SpringForce(target)
                .setDampingRatio(0.72f)
                .setStiffness(650f);
        SpringAnimation scaleX = new SpringAnimation(view, DynamicAnimation.SCALE_X);
        SpringAnimation scaleY = new SpringAnimation(view, DynamicAnimation.SCALE_Y);
        scaleX.setSpring(forceX);
        scaleY.setSpring(forceY);
        scaleX.start();
        scaleY.start();
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (activeAdminSession != null) {
            String review = activeAdminSession.review == null
                    ? "" : activeAdminSession.review;
            finishActiveAdminSession((review +
                    "\nEnded automatically when the app left the foreground.").trim(),
                    false);
        }
        if (connected) {
            currentMotorValue = 0;
            writeMotorValue(0);
            if (motorSlider != null) {
                motorSlider.setValue(0);
            }
            if (motorDial != null) {
                motorDial.setValue(0);
            }
        }
    }

    @Override
    @SuppressLint("MissingPermission")
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        stopScan();
        ptpActivityStore.finishConnection(System.currentTimeMillis());
        if (bluetoothGatt != null) {
            bluetoothGatt.disconnect();
            bluetoothGatt.close();
            bluetoothGatt = null;
        }
        super.onDestroy();
    }

    private static final class AdminHistoryItem {
        final AdminDataStore.PatientRecord patient;
        final AdminDataStore.SessionRecord session;

        AdminHistoryItem(AdminDataStore.PatientRecord patient,
                         AdminDataStore.SessionRecord session) {
            this.patient = patient;
            this.session = session;
        }
    }

    private abstract static class SimpleSeekListener
            implements SeekBar.OnSeekBarChangeListener {
        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }
    }
}
