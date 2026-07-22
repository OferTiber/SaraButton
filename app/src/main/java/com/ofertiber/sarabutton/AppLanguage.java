package com.ofertiber.sarabutton;

import android.app.LocaleManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.LocaleList;

import java.util.Locale;

final class AppLanguage {
    private static final String KEY_LANGUAGE = "app_language";
    private static final String[] LANGUAGE_TAGS = {
            "",
            "en",
            "he",
            "ar",
            "de",
            "es",
            "fr",
            "hi",
            "ja",
            "pt-BR",
            "ru",
            "zh-CN"
    };

    private AppLanguage() {
    }

    static Context wrap(Context context) {
        String languageTag = getSelectedLanguageTag(context);
        if (languageTag.isEmpty()) {
            Locale currentLocale = currentLocale(context.getResources().getConfiguration());
            Locale.setDefault(currentLocale);
            return context;
        }

        Locale locale = Locale.forLanguageTag(languageTag);
        Locale.setDefault(locale);
        Configuration configuration = new Configuration(
                context.getResources().getConfiguration()
        );
        configuration.setLocale(locale);
        configuration.setLayoutDirection(locale);
        return context.createConfigurationContext(configuration);
    }

    static String getSelectedLanguageTag(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            LocaleManager manager = context.getSystemService(LocaleManager.class);
            if (manager != null) {
                LocaleList locales = manager.getApplicationLocales();
                return locales.isEmpty() ? "" : locales.get(0).toLanguageTag();
            }
        }
        String storedTag = preferences(context).getString(KEY_LANGUAGE, "");
        return storedTag == null ? "" : storedTag;
    }

    static void setSelectedLanguageTag(Context context, String languageTag) {
        String normalizedTag = languageTag == null ? "" : languageTag;
        preferences(context).edit().putString(KEY_LANGUAGE, normalizedTag).apply();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            LocaleManager manager = context.getSystemService(LocaleManager.class);
            if (manager != null) {
                LocaleList locales = normalizedTag.isEmpty()
                        ? LocaleList.getEmptyLocaleList()
                        : LocaleList.forLanguageTags(normalizedTag);
                manager.setApplicationLocales(locales);
            }
        }
    }

    static int getLanguageCount() {
        return LANGUAGE_TAGS.length;
    }

    static String getLanguageTag(int position) {
        return LANGUAGE_TAGS[position];
    }

    static int getSelectedLanguagePosition(Context context) {
        String selectedTag = getSelectedLanguageTag(context);
        if (selectedTag.isEmpty()) {
            return 0;
        }
        for (int index = 1; index < LANGUAGE_TAGS.length; index++) {
            if (LANGUAGE_TAGS[index].equalsIgnoreCase(selectedTag)) {
                return index;
            }
        }
        Locale selectedLocale = Locale.forLanguageTag(selectedTag);
        for (int index = 1; index < LANGUAGE_TAGS.length; index++) {
            Locale optionLocale = Locale.forLanguageTag(LANGUAGE_TAGS[index]);
            if (optionLocale.getLanguage().equals(selectedLocale.getLanguage())) {
                return index;
            }
        }
        return 0;
    }

    private static SharedPreferences preferences(Context context) {
        Context appContext = context.getApplicationContext();
        Context deviceContext = AppPreferences.storageContext(
                appContext == null ? context : appContext
        );
        return deviceContext.getSharedPreferences(AppPreferences.FILE, Context.MODE_PRIVATE);
    }

    static Locale currentLocale(Configuration configuration) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return configuration.getLocales().get(0);
        }
        //noinspection deprecation
        return configuration.locale;
    }
}
