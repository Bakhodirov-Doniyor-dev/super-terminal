package com.example

import com.example.db.ScriptEntity

object Translations {
    private val translations = mapOf(
        "nav_apps" to mapOf(
            "uz" to "Ilovalar",
            "en" to "Apps",
            "ru" to "Приложения"
        ),
        "nav_monitor" to mapOf(
            "uz" to "Monitor",
            "en" to "Monitor",
            "ru" to "Мониторинг"
        ),
        "nav_files" to mapOf(
            "uz" to "Fayllar",
            "en" to "Files",
            "ru" to "Файлы"
        ),
        "nav_security" to mapOf(
            "uz" to "Himoya",
            "en" to "Security",
            "ru" to "Защита"
        ),
        "nav_terminal" to mapOf(
            "uz" to "Terminal",
            "en" to "Terminal",
            "ru" to "Терминал"
        ),
        "nav_scripts" to mapOf(
            "uz" to "Skriptlar",
            "en" to "Scripts",
            "ru" to "Скрипты"
        ),
        "nav_history" to mapOf(
            "uz" to "Tarix",
            "en" to "History",
            "ru" to "История"
        ),
        "nav_about" to mapOf(
            "uz" to "Ma'lumot",
            "en" to "About",
            "ru" to "Инфо"
        ),
        "settings_title" to mapOf(
            "uz" to "Sozlamalar",
            "en" to "Settings",
            "ru" to "Настройки"
        ),
        "tab_settings" to mapOf(
            "uz" to "Sozlamalar",
            "en" to "Settings",
            "ru" to "Настройки"
        ),
        "tab_scripts" to mapOf(
            "uz" to "Skriptlar",
            "en" to "Scripts",
            "ru" to "Скрипты"
        ),
        "tab_history" to mapOf(
            "uz" to "Tarix",
            "en" to "History",
            "ru" to "История"
        ),
        "tab_system_info" to mapOf(
            "uz" to "Tizim Info",
            "en" to "System Info",
            "ru" to "О системе"
        ),
        "tab_pc_permissions" to mapOf(
            "uz" to "PC Ruxsatlar",
            "en" to "PC Permissions",
            "ru" to "Права ПК"
        ),
        "language_select_label" to mapOf(
            "uz" to "Ilova tili (Language):",
            "en" to "App Language:",
            "ru" to "Язык приложения:"
        ),
        "settings_subtitle" to mapOf(
            "uz" to "Terminal muloqot tizimi sozlamalari:",
            "en" to "Terminal session settings:",
            "ru" to "Параметры сессии терминала:"
        ),
        "settings_theme_label" to mapOf(
            "uz" to "Terminal Mavzusi (Theme):",
            "en" to "Terminal Theme:",
            "ru" to "Тема терминала:"
        ),
        "settings_font_size_label" to mapOf(
            "uz" to "Terminal Shrift o'lchami:",
            "en" to "Terminal Font Size:",
            "ru" to "Размер шрифта:"
        ),
        "settings_history_limit_label" to mapOf(
            "uz" to "Tarixdagi buyruqlar limiti:",
            "en" to "Command History Limit:",
            "ru" to "Лимит истории команд:"
        ),
        "settings_auto_scroll_title" to mapOf(
            "uz" to "Avtomatik pastga tushish",
            "en" to "Auto Scroll",
            "ru" to "Автопрокрутка"
        ),
        "settings_tts_title" to mapOf(
            "uz" to "Ovozli o'qish (TTS)",
            "en" to "Text to Speech (TTS)",
            "ru" to "Голосовое чтение (TTS)"
        ),
        "settings_tts_desc" to mapOf(
            "uz" to "Terminal buyruq natijalarini ovozli ijro etish.",
            "en" to "Play terminal command results as audio playback.",
            "ru" to "Озвучивать результаты команд терминала."
        ),
        "settings_auto_scroll_desc" to mapOf(
            "uz" to "Yangi natijalar kelsa terminal pastga siljiydi.",
            "en" to "The terminal automatically scrolls down for new results.",
            "ru" to "Терминал прокручивается вниз при новых строках."
        ),
        "settings_dev_options_title" to mapOf(
            "uz" to "Dasturchi Sozlamalari",
            "en" to "Developer Options",
            "ru" to "Настройки разработчика"
        ),
        "settings_dev_options_desc" to mapOf(
            "uz" to "Telefonda 'Developer' menyusini ochish.",
            "en" to "Open 'Developer Options' on the device.",
            "ru" to "Открыть меню разработчика на телефоне."
        ),
        "settings_wifi_settings_title" to mapOf(
            "uz" to "Wi-Fi Sozlamalari",
            "en" to "Wi-Fi Settings",
            "ru" to "Настройки Wi-Fi"
        ),
        "settings_wifi_settings_desc" to mapOf(
            "uz" to "Port ko'rish uchun Wi-Fi sahifasini ochadi.",
            "en" to "Opens the Wi-Fi settings screen to view details.",
            "ru" to "Открывает Wi-Fi для просмотра порта."
        ),
        "settings_reset_scripts_title" to mapOf(
            "uz" to "Skript asliga",
            "en" to "Restore",
            "ru" to "Скрипты по"
        ),
        "settings_reset_scripts_desc" to mapOf(
            "uz" to "Qaytarish",
            "en" to "Scripts",
            "ru" to "умолчанию"
        ),
        "settings_disconnect_title" to mapOf(
            "uz" to "Kanal uzish",
            "en" to "Disconnect",
            "ru" to "Отключить"
        ),
        "settings_disconnect_desc" to mapOf(
            "uz" to "ADB qayta yuklash",
            "en" to "ADB reload",
            "ru" to "Перезапуск ADB"
        ),
        "btn_close" to mapOf(
            "uz" to "Yopish",
            "en" to "Close",
            "ru" to "Закрыть"
        ),
        "btn_cancel" to mapOf(
            "uz" to "Bekor qilish",
            "en" to "Cancel",
            "ru" to "Отмена"
        ),
        "btn_save" to mapOf(
            "uz" to "Saqlash",
            "en" to "Save",
            "ru" to "Сохранить"
        ),
        "btn_clear" to mapOf(
            "uz" to "Tozalash",
            "en" to "Clear",
            "ru" to "Очистить"
        ),
        "btn_add" to mapOf(
            "uz" to "Qo'shish",
            "en" to "Add",
            "ru" to "Добавить"
        ),
        "search_scripts_placeholder" to mapOf(
            "uz" to "Skriptlarni qidirish...",
            "en" to "Search scripts...",
            "ru" to "Поиск скриптов..."
        ),
        "search_tabs_placeholder" to mapOf(
            "uz" to "Oynani qidirish...",
            "en" to "Search tabs...",
            "ru" to "Поиск окон..."
        ),
        "tabs_count_label" to mapOf(
            "uz" to "Oynalar",
            "en" to "Tabs",
            "ru" to "Вкладки"
        ),
        "about_title" to mapOf(
            "uz" to "Super Terminal Manager Haqida",
            "en" to "About Super Terminal Manager",
            "ru" to "О приложении Super Terminal Manager"
        ),
        "about_version" to mapOf(
            "uz" to "Talqin",
            "en" to "Version",
            "ru" to "Версия"
        ),
        "about_version_label" to mapOf(
            "uz" to "Talqin",
            "en" to "Version",
            "ru" to "Версия"
        ),
        "about_desc" to mapOf(
            "uz" to "Ushbu ilova simsiz va mahalliy terminal buyruqlarini sinash hamda telefoningizni boshqarish uchun professional vositadir. Port ulanishi va mavzular to'liq yangi desktop dizayniga moslab shakllantirilgan.",
            "en" to "This application is a professional tool for testing wireless and local terminal commands and controlling your device. Features desktop layouts, multiple tabs, and custom command histories.",
            "ru" to "Это приложение — профессиональный инструмент для выполнения беспроводных и локальных терминальных команд. Поддерживает вкладки, кастомные темы и историю."
        ),
        "about_creator" to mapOf(
            "uz" to "Yaratuvchi: Bahodirov Doniyor",
            "en" to "Developer: Bahodirov Doniyor",
            "ru" to "Разработчик: Баходиров Дониёр"
        ),
        "privacy_policy_title" to mapOf(
            "uz" to "Maxfiylik Siyosati (Privacy Policy)",
            "en" to "Privacy Policy",
            "ru" to "Политика конфиденциальности"
        ),
        "privacy_policy_content" to mapOf(
            "uz" to "Ushbu Terminal ilovasi shifrlangan va xavfsiz bo'lib, foydalanuvchidan hech qanday shaxsiy ma'lumotlarni yig'maydi va serverlarga uzatmaydi. Barcha buyruqlar va ma'lumotlar to'liq telefoningiz ichida lokal ravishda saqlanadi hamda tahlil qilinadi.\n\nPlay Market talablariga mos maxfiylik siyosati rasmiy havolasi:\nhttps://sites.google.com/view/bahodirov/home",
            "en" to "This Terminal app is secure and encrypted. It does not collect or transmit any personal user data or history to external servers. All command execution and history analytics remain 100% local on your device.\n\nPlay Store Compliant Privacy Policy URL:\nhttps://sites.google.com/view/bahodirov/home",
            "ru" to "Данное приложение Терминал является безопасным и зашифрованным. Оно не собирает и не передает личные данные или историю команд на сторонние серверы. Все процессы выполняются локально на вашем устройстве.\n\nОфициальная ссылка для Google Play:\nhttps://sites.google.com/view/bahodirov/home"
        ),
        "app_signing_title" to mapOf(
            "uz" to "Imzo va Play Marketga Yuklash",
            "en" to "App Signing & Google Play",
            "ru" to "Цифровая подпись и Google Play"
        ),
        "app_signing_content" to mapOf(
            "uz" to "⚠️ Nega telefon uni havfli deb o'ylaydi?\nHozirda yuklab olingan test APK 'Debug Imzosi' bilan imzolangan. Android xavfsizlik tizimi bunday norasmiy developer imzoli ilovalarni 'Play Protect' orqali vaqtincha ogohlantiradi.\n\n🚀 Play Marketga qanday yuklash mumkin?\n1. Ilova kodini ZIP variantida yuklab oling (AI Studio sozlamalarida).\n2. Android Studio-da loyihani oching.\n3. Build -> Generate Signed Bundle / APK menyusiga kiring va o'zingizning maxfiy Keystore (.jks) kalitingiz bilan imzolang.\n4. Tayyor bo'lgan .aab formatidagi faylni erkin Google Play Console-ga yuklang. Shunda barcha ogohlantirishlar yo'qoladi!",
            "en" to "⚠️ Why does Play Protect say it is dangerous?\nThe current test APK is signed with a temporary 'Debug Key'. Google Play Protect warns against debug-signed APKs because they are created for development and testing purposes.\n\n🚀 How to upload to Google Play Store?\n1. Download the full source code as a ZIP file (from the AI Studio sidebar/settings).\n2. Open the project in Android Studio.\n3. Go to Build -> Generate Signed Bundle / APK, create your custom Keystore (.jks), and sign the app.\n4. Upload the generated .aab (bundle) to your Google Play Console! All warnings will disappear easily.",
            "ru" to "⚠️ Почему система пишет, что приложение опасно?\nТекущий APK подписан временной разработческой 'Debug подписью'. Защита Google Play Protect предупреждает об установке таких тестовых файлов.\n\n🚀 Как опубликовать в Google Play?\n1. Скачайте проект сборки в формате ZIP (через боковое меню настроек AI Studio).\n2. Откройте проект в Android Studio.\n3. Перейдите в Build -> Generate Signed Bundle / APK, создайте свой цифровой ключ (.jks) и подпишите приложение.\n4. Загрузите готовый файл формата .aab в Google Play Console! Предупреждения исчезнут."
        ),
        "btn_privacy" to mapOf(
            "uz" to "Maxfiylik Siyosati",
            "en" to "Privacy Policy",
            "ru" to "Политика конфиденциальности"
        ),
        "btn_signing" to mapOf(
            "uz" to "Google Play va Imzo",
            "en" to "App Signing Info",
            "ru" to "Подпись и Google Play"
        ),
        "shortcuts_title" to mapOf(
            "uz" to "Terminal Tezkor Buyruqlari",
            "en" to "Terminal Quick Shortcuts",
            "ru" to "Быстрые команды"
        ),
        "shortcuts_ok" to mapOf(
            "uz" to "Tushunarli",
            "en" to "Understood",
            "ru" to "Понятно"
        ),
        "new_tab" to mapOf(
            "uz" to "Yangi Oyna",
            "en" to "New Tab",
            "ru" to "Новая вкладка"
        ),
        "shortcut_clear" to mapOf(
            "uz" to "• 'clear' yoki 'reset' - Terminal ekranini tozalash",
            "en" to "• 'clear' or 'reset' - Clear terminal screen",
            "ru" to "• 'clear' или 'reset' - Очистить экран терминала"
        ),
        "shortcut_packages" to mapOf(
            "uz" to "• 'pm list packages -3' - Tashqi ilovalar ro'yxatini ko'rish",
            "en" to "• 'pm list packages -3' - View third-party app packages",
            "ru" to "• 'pm list packages -3' - Список сторонних приложений"
        ),
        "shortcut_battery" to mapOf(
            "uz" to "• 'dumpsys battery' - Batareya diagnostikasini chiqarish",
            "en" to "• 'dumpsys battery' - Get battery status diagnostics",
            "ru" to "• 'dumpsys battery' - Вывести диагностику батареи"
        ),
        "add_script_title" to mapOf(
            "uz" to "Yangi Skript Qo'shish",
            "en" to "Add New Script",
            "ru" to "Добавить новый скрипт"
        ),
        "label_title" to mapOf(
            "uz" to "Skript nomi (Title)",
            "en" to "Script Label (Title)",
            "ru" to "Название скрипта"
        ),
        "label_command" to mapOf(
            "uz" to "ADB Shell Buyrug'i",
            "en" to "ADB Shell Command",
            "ru" to "Команда ADB Shell"
        ),
        "label_desc" to mapOf(
            "uz" to "Tarif / Maqsad",
            "en" to "Description / Purpose",
            "ru" to "Описание / Назначение"
        ),
        "label_category" to mapOf(
            "uz" to "Kategoriya Tanlang:",
            "en" to "Select Category:",
            "ru" to "Выберите категорию:"
        ),
        "cat_all" to mapOf(
            "uz" to "Barchasi",
            "en" to "All",
            "ru" to "Все"
        ),
        "cat_sys" to mapOf(
            "uz" to "Tizim",
            "en" to "System",
            "ru" to "Система"
        ),
        "cat_pkg" to mapOf(
            "uz" to "Paketlar",
            "en" to "Packages",
            "ru" to "Пакеты"
        ),
        "cat_net" to mapOf(
            "uz" to "Tarmoq",
            "en" to "Network",
            "ru" to "Сеть"
        ),
        "cat_scr" to mapOf(
            "uz" to "Ekran",
            "en" to "Screen",
            "ru" to "Экран"
        ),
        "cat_oth" to mapOf(
            "uz" to "Boshqa",
            "en" to "Other",
            "ru" to "Другое"
        ),
        "computer_permissions_title" to mapOf(
            "uz" to "Kompyuter orqali ulanish",
            "en" to "PC Port Connection",
            "ru" to "Подключение через ПК"
        ),
        "computer_permissions_desc" to mapOf(
            "uz" to "Simsiz debug interfeysini 5555 standart portiga sozlashingiz uchun kompyuteringiz terminalida quyidagi buyruqlarni erkin va xavfsiz bajaring:",
            "en" to "To direct port redirection over safety-verified standard port 5555, simply execute these commands in your computer's terminal:",
            "ru" to "Чтобы перенаправить беспроводной порт на стандартный 5555, выполните эти безопасные команды в терминале ПК:"
        ),
        "copy_all_commands" to mapOf(
            "uz" to "Barcha buyruqlarni ko'chirish",
            "en" to "Copy all commands",
            "ru" to "Копировать все команды"
        ),
        "history_empty" to mapOf(
            "uz" to "Terminal buyruqlar tarixi bo'sh.",
            "en" to "Terminal command history is empty.",
            "ru" to "История пуста."
        ),
        "history_clear_success" to mapOf(
            "uz" to "Tarix muvaffaqiyatli tozalandi!",
            "en" to "History successfully cleared!",
            "ru" to "История успешно очищена!"
        ),
        "command_run_again" to mapOf(
            "uz" to "Yana bajarish",
            "en" to "Run again",
            "ru" to "Выполнить снова"
        ),
        "command_replayed" to mapOf(
            "uz" to "Buyruq qayta ishlatildi!",
            "en" to "Command replayed successfully!",
            "ru" to "Команда выполнена повторно!"
        ),
        "script_restored" to mapOf(
            "uz" to "Skriptlar holati asliga qaytarildi",
            "en" to "Scripts successfully restored to default",
            "ru" to "Состояние скриптов успешно сброшено"
        ),
        "script_deleted" to mapOf(
            "uz" to "Skript o'chirib tashlandi!",
            "en" to "Script deleted successfully!",
            "ru" to "Скрипт успешно удален!"
        ),
        "help_instructions" to mapOf(
            "uz" to "Android 10 va undan pastki tizimlar",
            "en" to "Android 10 and lower systems",
            "ru" to "Системы Android 10 и ниже"
        ),
        "help_guide" to mapOf(
            "uz" to "Qo'llanma",
            "en" to "Guide",
            "ru" to "Руководство"
        ),
        "help_instructions_desc" to mapOf(
            "uz" to "Android 10 va undan pastki versiyali qurilmalarda simsiz ulanish portlari Developer saytida to'liq ko'rinmaydi. Buning uchun avval ilovani kompyuterga ulab 'adb tcpip 5555' buyrug'ini bajarishingiz kerak.",
            "en" to "Wireless debugging ports are not fully visible in the Developer settings on Android 10 and lower. For this, you must first connect the device to a computer and execute 'adb tcpip 5555'.",
            "ru" to "Порты беспроводной отладки на Android 10 и ниже не отображаются в настройках. Сначала подключите устройство к ПК и выполните команду 'adb tcpip 5555'."
        ),
        "script_saved" to mapOf(
            "uz" to "Yangi skript muvaffaqiyatli saqlandi! 🎉",
            "en" to "New script successfully saved! 🎉",
            "ru" to "Новый скрипт успешно сохранен! 🎉"
        ),
        "script_invalid" to mapOf(
            "uz" to "Iltimos, barcha maydonlarni majburiy to'ldiring!",
            "en" to "Please fill in all the fields!",
            "ru" to "Пожалуйста, заполните все поля!"
        ),
        "cl_esc" to mapOf(
            "uz" to "ESC",
            "en" to "ESC",
            "ru" to "ESC"
        ),
        "cl_tab" to mapOf(
            "uz" to "TAB",
            "en" to "TAB",
            "ru" to "TAB"
        ),
        "cl_ctrl" to mapOf(
            "uz" to "CTRL",
            "en" to "CTRL",
            "ru" to "CTRL"
        ),
        "cl_alt" to mapOf(
            "uz" to "ALT",
            "en" to "ALT",
            "ru" to "ALT"
        ),
        "cl_cls" to mapOf(
            "uz" to "CLSR",
            "en" to "CLSR",
            "ru" to "CLSR"
        ),
        "scripts_tab_title" to mapOf(
            "uz" to "Professional Sozlash Skriptlari",
            "en" to "Professional Setup Scripts",
            "ru" to "Профессиональные скрипты"
        ),
        "scripts_search_placeholder" to mapOf(
            "uz" to "Skriptlarni qidirish...",
            "en" to "Search scripts...",
            "ru" to "Поиск скриптов..."
        ),
        "scripts_empty" to mapOf(
            "uz" to "Hech qanday skript topilmadi.",
            "en" to "No scripts found.",
            "ru" to "Скрипты не найдены."
        ),
        "run_script_btn" to mapOf(
            "uz" to "Skriptni terminalda bajarish",
            "en" to "Run script in terminal",
            "ru" to "Запустить скрипт в терминале"
        ),
        "save" to mapOf(
            "uz" to "Saqlash",
            "en" to "Save",
            "ru" to "Сохранить"
        ),
        "cancel" to mapOf(
            "uz" to "Bekor qilish",
            "en" to "Cancel",
            "ru" to "Отмена"
        ),
        "history_tab_title" to mapOf(
            "uz" to "Terminal Amallari Tarixi",
            "en" to "Terminal Action History",
            "ru" to "История команд терминала"
        ),
        "history_clear_btn" to mapOf(
            "uz" to "Tozalash",
            "en" to "Clear All",
            "ru" to "Очистить"
        ),
        "connect_label" to mapOf(
            "uz" to "Wireless ADB Ulanishi (PORT ULASH)",
            "en" to "Wireless ADB Connection (PORT)",
            "ru" to "Подключение Wireless ADB (ПОРТ)"
        ),
        "active_conn" to mapOf(
            "uz" to "Faol ulanish",
            "en" to "Active connection",
            "ru" to "Активное подключение"
        ),
        "disconnected" to mapOf(
            "uz" to "Ulanmagan (Local host shell)",
            "en" to "Disconnected (Local host shell)",
            "ru" to "Отключено (Локальный shell)"
        ),
        "port" to mapOf(
            "uz" to "Port",
            "en" to "Port",
            "ru" to "Порт"
        ),
        "status" to mapOf(
            "uz" to "Aloqa holati",
            "en" to "Connection status",
            "ru" to "Статус подключения"
        ),
        "run_script_terminal" to mapOf(
            "uz" to "Skript Terminalda ishga tushirildi!",
            "en" to "Script executed in Terminal!",
            "ru" to "Скрипт запущен в Терминале!"
        ),
        "menu_new_tab" to mapOf(
            "uz" to "Yangi Oyna",
            "en" to "New Tab",
            "ru" to "Новая вкладка"
        ),
        "menu_show_tabs" to mapOf(
            "uz" to "Oynalarni qidirish",
            "en" to "Show Open Tabs",
            "ru" to "Показать вкладки"
        ),
        "menu_fullscreen" to mapOf(
            "uz" to "Butun ekran rejimi",
            "en" to "Fullscreen",
            "ru" to "Полный экран"
        ),
        "menu_preferences" to mapOf(
            "uz" to "Sozlamalar",
            "en" to "Preferences",
            "ru" to "Настройки"
        ),
        "menu_shortcuts" to mapOf(
            "uz" to "Klaviatura buyruqlari",
            "en" to "Keyboard Shortcuts",
            "ru" to "Горячие клавиши"
        ),
        "menu_check_updates" to mapOf(
            "uz" to "Ilovani yangilash (OTA)",
            "en" to "Check for Updates",
            "ru" to "Проверить обновления"
        ),
        "menu_about" to mapOf(
            "uz" to "Terminal haqida",
            "en" to "About",
            "ru" to "О терминале"
        ),
        "dropdown_layout_theme" to mapOf(
            "uz" to "Mavzu",
            "en" to "Layout Theme",
            "ru" to "Тема"
        ),
        "dropdown_font_scale" to mapOf(
            "uz" to "Shrift o'lchami",
            "en" to "Font Scale",
            "ru" to "Размер шрифта"
        ),
        "menu_connect_wireless_port" to mapOf(
            "uz" to "Simsiz Portga Ulanish",
            "en" to "Connect Wireless Port",
            "ru" to "Подключить беспроводной порт"
        ),
        "status_connected" to mapOf(
            "uz" to "Ulangan",
            "en" to "Connected",
            "ru" to "Подключено"
        ),
        "status_disconnected" to mapOf(
            "uz" to "Ulanmagan",
            "en" to "Disconnected",
            "ru" to "Отключено"
        ),
        "btn_connect" to mapOf(
            "uz" to "Ulanish",
            "en" to "Connect",
            "ru" to "Подключить"
        ),
        "btn_disconnect" to mapOf(
            "uz" to "Uzish",
            "en" to "Disconnect",
            "ru" to "Отключить"
        ),
        "device_diagnostic_title" to mapOf(
            "uz" to "Tizim va Qurilma Tashxisi",
            "en" to "System & Device Diagnostics",
            "ru" to "Диагностика системы"
        ),
        "sub_diagnostic_adb" to mapOf(
            "uz" to "Faol wireless adb tizim o'zgaruvchilari",
            "en" to "Active wireless adb properties",
            "ru" to "Активные свойства wireless adb"
        ),
        "sub_diagnostic_local" to mapOf(
            "uz" to "Mahalliy qurilma tizim xarakteristikalari",
            "en" to "Local device system properties",
            "ru" to "Локальные свойства системы устройства"
        ),
        "banner_diagnostic_adb" to mapOf(
            "uz" to "Wireless ADB Bog'lanish Faol",
            "en" to "Wireless ADB Connection Active",
            "ru" to "Подключение Wireless ADB активно"
        ),
        "banner_diagnostic_local" to mapOf(
            "uz" to "Lokal Rejim (Port Ulanmagan)",
            "en" to "Local Mode",
            "ru" to "Локальный режим"
        ),
        "desc_diagnostic_adb" to mapOf(
            "uz" to "Barcha tizim parametrlari bevosita terminal ulanishi orqali o'qildi.",
            "en" to "All system parameters are retrieved via active terminal connections.",
            "ru" to "Все системные параметры получены через активное подключение терминала."
        ),
        "desc_diagnostic_local" to mapOf(
            "uz" to "Ba'zi ma'lumotlar faqat adb ulanishi orqali o'qiladi. Port ulash bo'limidan adb ni ulang.",
            "en" to "Some details are only readable via adb connection. Connect adb on the Port tab.",
            "ru" to "Некоторые данные доступны только через adb. Подключите adb на вкладке порта."
        ),
        "cm_title" to mapOf(
            "uz" to "ADB Ulanishlar Menejeri",
            "en" to "ADB Connection Manager",
            "ru" to "Менеджер подключений ADB"
        ),
        "cm_mode_single" to mapOf(
            "uz" to "Yakka qurilma",
            "en" to "Single Device",
            "ru" to "Одиночный режим"
        ),
        "cm_mode_multi" to mapOf(
            "uz" to "Multi-Device Mode",
            "en" to "Multi-Device Mode",
            "ru" to "Мульти-устройство"
        ),
        "cm_select_all" to mapOf(
            "uz" to "Barchasini tanlash",
            "en" to "Select All",
            "ru" to "Выбрать все"
        ),
        "cm_deselect_all" to mapOf(
            "uz" to "Tanlovni tozalash",
            "en" to "Clear",
            "ru" to "Очистить"
        ),
        "cm_btn_scan_usb" to mapOf(
            "uz" to "USB OTG izlash",
            "en" to "Scan USB",
            "ru" to "Поиск USB"
        ),
        "cm_btn_add_device" to mapOf(
            "uz" to "Qurilma qo'shish",
            "en" to "Add Device",
            "ru" to "Добавить"
        ),
        "cm_run_broadcast" to mapOf(
            "uz" to "Buyruqni yuborish",
            "en" to "Run Command",
            "ru" to "Выполнить команду"
        ),
        "cm_results_table" to mapOf(
            "uz" to "Natijalar jadvali",
            "en" to "Results Table",
            "ru" to "Таблица результатов"
        ),
        "cm_col_device" to mapOf(
            "uz" to "Qurilma",
            "en" to "Device",
            "ru" to "Устройство"
        ),
        "cm_col_type" to mapOf(
            "uz" to "Turi",
            "en" to "Type",
            "ru" to "Тип"
        ),
        "cm_col_status" to mapOf(
            "uz" to "Holat",
            "en" to "Status",
            "ru" to "Статус"
        ),
        "cm_col_latency" to mapOf(
            "uz" to "Tezlik",
            "en" to "Latency",
            "ru" to "Задержка"
        ),
        "cm_col_output" to mapOf(
            "uz" to "Natija",
            "en" to "Output",
            "ru" to "Вывод"
        ),
        "cm_make_active" to mapOf(
            "uz" to "Terminalga ulash",
            "en" to "Set Active",
            "ru" to "В терминал"
        ),
        "app_action_shield" to mapOf(
            "uz" to "Himoyani Tekshirish & Zararsizlantirish",
            "en" to "Shield & Anti-Lock Manager",
            "ru" to "Анализ защиты и блокировок"
        ),
        "app_action_uninstall" to mapOf(
            "uz" to "O'chirish (Uninstall)",
            "en" to "Uninstall",
            "ru" to "Удалить"
        ),
        "app_action_freeze" to mapOf(
            "uz" to "Muzlatish (O'chirmasdan)",
            "en" to "Freeze (Disable)",
            "ru" to "Заморозить"
        ),
        "app_action_unfreeze" to mapOf(
            "uz" to "Faollashtirish (Qayta yoqish)",
            "en" to "Unfreeze (Enable)",
            "ru" to "Разморозить"
        ),
        "app_action_copy_pkg" to mapOf(
            "uz" to "Paket nomini nusxalash",
            "en" to "Copy Package Name",
            "ru" to "Копировать имя пакета"
        ),
        "app_neutralizer_title" to mapOf(
            "uz" to "Himoyalangan Ilova Boshqaruvi",
            "en" to "Protected App Neutralizer",
            "ru" to "Управление защищенным приложением"
        ),
        "app_neutralizer_analysis" to mapOf(
            "uz" to "QALQON VA XAVFSIZLIK TAHLILI:",
            "en" to "PROTECTION & SHIELD ANALYSIS:",
            "ru" to "АНАЛИЗ ЗАЩИТЫ И БЛОКИРОВОК:"
        ),
        "app_neutralizer_method1" to mapOf(
            "uz" to "USUL 1: SIMSIZ ADB BILAN 1-BOSQICHDA (TAVSIYA)",
            "en" to "METHOD 1: VIA WIRELESS ADB (RECOMMENDED)",
            "ru" to "СПОСОБ 1: ЧЕРЕЗ WIRELESS ADB (РЕКОМЕНДУЕТСЯ)"
        ),
        "app_neutralizer_method2" to mapOf(
            "uz" to "USUL 2: HIMOYANI QO'LDA O'CHIRISH (QADAM-BA-QADAM)",
            "en" to "METHOD 2: MANUAL SHIELD DEACTIVATION",
            "ru" to "СПОСОБ 2: РУЧНОЕ СНЯТИЕ ЗАЩИТЫ (ПОШАГОВО)"
        ),
        "app_force_nuke_btn" to mapOf(
            "uz" to "Majburiy O'chirish",
            "en" to "Force Nuke",
            "ru" to "Принуд. удалить"
        ),
        "app_force_freeze_btn" to mapOf(
            "uz" to "Muzlatish",
            "en" to "Freeze",
            "ru" to "Заморозить"
        ),
        "app_force_unfreeze_btn" to mapOf(
            "uz" to "Faollashtirish",
            "en" to "Unfreeze",
            "ru" to "Разморозить"
        ),
        "app_step1_overlay" to mapOf(
            "uz" to "Ekran Ustidan Qulflashni (Overlay) O'chirish",
            "en" to "Disable Draw Over Other Apps",
            "ru" to "Отключить показ поверх окон (Overlay)"
        ),
        "app_step2_accessibility" to mapOf(
            "uz" to "Maxsus Imkoniyatlarni (Accessibility) O'chirish",
            "en" to "Disable Accessibility Service",
            "ru" to "Отключить спец. возможности (Accessibility)"
        ),
        "app_step3_device_admin" to mapOf(
            "uz" to "Tizim Ma'muri (Device Admin) Huquqini Bekor Qilish",
            "en" to "Deactivate Device Admin",
            "ru" to "Снять статус Администратора устройства"
        ),
        "app_step4_uninstall" to mapOf(
            "uz" to "Ilovani O'chirish (Uninstall)",
            "en" to "Uninstall App",
            "ru" to "Удалить приложение"
        ),
        "app_connect_adb_wireless_btn" to mapOf(
            "uz" to "Simsiz ADB ni Ulash (Wireless Pairing)",
            "en" to "Connect Wireless ADB",
            "ru" to "Подключить Wireless ADB"
        ),
        "anti_overlay_title" to mapOf(
            "uz" to "24/7 Ekran Qalqoni (Anti-Overlay Sentry)",
            "en" to "24/7 Anti-Overlay Sentry",
            "ru" to "24/7 Экранный Щит (Anti-Overlay)"
        ),
        "anti_overlay_desc" to mapOf(
            "uz" to "Boshqa ilovalar ekranni noqonuniy to'sib olishini 24/7 real-vaqtda aniqlaydi va ruxsatini darhol o'chiradi.",
            "en" to "Real-time 24/7 sentinel that detects and revokes screen-blocking overlays instantly.",
            "ru" to "В реальном времени 24/7 обнаруживает и отзывает права приложений, блокирующих экран поверх окон."
        ),
        "anti_overlay_sentry_active" to mapOf(
            "uz" to "QALQON 24/7 FAOL",
            "en" to "SENTRY 24/7 ACTIVE",
            "ru" to "ЩИТ 24/7 АКТИВЕН"
        ),
        "anti_overlay_sentry_inactive" to mapOf(
            "uz" to "QALQON O'CHIRILGAN",
            "en" to "SENTRY DISABLED",
            "ru" to "ЩИТ ОТКЛЮЧЕН"
        ),
        "anti_overlay_toggle_title" to mapOf(
            "uz" to "24/7 Real-Vaqtda Ekranni Himoya Qilish",
            "en" to "24/7 Real-Time Screen Guard",
            "ru" to "24/7 Защита экрана в реальном времени"
        ),
        "anti_overlay_toggle_desc" to mapOf(
            "uz" to "Ilovadan chiqsangiz ham fonda ishlab turadi va ekran to'silganda zudlik bilan aralashadi.",
            "en" to "Runs persistently in background to neutralize screen hijacking immediately.",
            "ru" to "Работает 24/7 в фоне и мгновенно нейтрализует блокировку экрана."
        ),
        "anti_overlay_aggressive_title" to mapOf(
            "uz" to "Agressiv Zararsizlantirish (Auto-Revoke & Kill)",
            "en" to "Aggressive Neutralization (Auto-Revoke & Kill)",
            "ru" to "Агрессивная нейтрализация (Авто-отзыв и остановка)"
        ),
        "anti_overlay_aggressive_desc" to mapOf(
            "uz" to "Ekran to'silganda AppOps (SYSTEM_ALERT_WINDOW) ruxsatini darhol IGNORE qiladi va ilovani majburan to'xtatadi.",
            "en" to "Instantly sets AppOps SYSTEM_ALERT_WINDOW to IGNORE and force-stops the app.",
            "ru" to "Мгновенно сбрасывает права AppOps в IGNORE и принудительно останавливает процесс."
        ),
        "anti_overlay_scan_now" to mapOf(
            "uz" to "Hozirgi Ekranni Skanerlash (Deep Scan)",
            "en" to "Deep Scan Active Windows",
            "ru" to "Глубокое сканирование окон"
        ),
        "anti_overlay_whitelist_title" to mapOf(
            "uz" to "Xavfsiz Ilovalar (Oq ro'yxat / Whitelist)",
            "en" to "Trusted Apps (Whitelist)",
            "ru" to "Белый список доверенных приложений"
        ),
        "anti_overlay_whitelist_desc" to mapOf(
            "uz" to "Bu ilovalarning oyna chizishi ruxsat etiladi (Tizim, Qo'ng'iroq, Launcher).",
            "en" to "These apps are permitted to draw overlays (System UI, Phone, Launcher).",
            "ru" to "Этим приложениям разрешено рисовать поверх экрана (Система, Телефон, Лаунчер)."
        ),
        "anti_overlay_history_title" to mapOf(
            "uz" to "Zararsizlantirish Tarixi (Audit Log)",
            "en" to "Neutralization Audit Logs",
            "ru" to "Журнал нейтрализации угроз"
        ),
        "anti_overlay_clear_history" to mapOf(
            "uz" to "Tarixni tozalash",
            "en" to "Clear Logs",
            "ru" to "Очистить журнал"
        ),
        "anti_overlay_threat_count" to mapOf(
            "uz" to "Zararsizlantirilgan Hujumlar",
            "en" to "Neutralized Threats",
            "ru" to "Нейтрализовано угроз"
        ),
        "anti_overlay_accessibility_card_title" to mapOf(
            "uz" to "Maxsus Imkoniyatlar Qalqoni (Sub-millisekund)",
            "en" to "Accessibility Ultra Shield (Sub-millisecond)",
            "ru" to "Щит спец. возможностей (Суб-миллисекундный)"
        ),
        "anti_overlay_accessibility_card_desc" to mapOf(
            "uz" to "Ekranda yangi oyna paydo bo'lishi bilan soniyaning mingdan birida ushlaydi.",
            "en" to "Catches new overlay windows with zero-latency window event hooks.",
            "ru" to "Перехватывает создание оверлеев с нулевой задержкой."
        ),
        "anti_overlay_open_accessibility" to mapOf(
            "uz" to "Maxsus Imkoniyatlarni Yoqish",
            "en" to "Enable Accessibility Shield",
            "ru" to "Включить спец. возможности"
        ),
        "device_admin_label" to mapOf(
            "uz" to "Tizim Ma'muri (Device Admin)",
            "en" to "Device Admin",
            "ru" to "Администратор устройства"
        ),
        "device_admin_active" to mapOf(
            "uz" to "FAOL (O'chirishni bloklaydi)",
            "en" to "ACTIVE (Blocks uninstall)",
            "ru" to "АКТИВЕН (Блокирует удаление)"
        ),
        "device_admin_inactive" to mapOf(
            "uz" to "Nofaol",
            "en" to "Inactive",
            "ru" to "Неактивен"
        ),
        "overlay_label" to mapOf(
            "uz" to "Ekran Ustidan Qulflash (Overlay)",
            "en" to "Draw Over Apps (Overlay)",
            "ru" to "Поверх других окон (Overlay)"
        ),
        "overlay_active" to mapOf(
            "uz" to "RUXSAT ETILGAN (Ekranni to'sadi)",
            "en" to "ALLOWED (Can block screen)",
            "ru" to "РАЗРЕШЕНО (Перекрывает экран)"
        ),
        "overlay_inactive" to mapOf(
            "uz" to "Ruxsatsiz",
            "en" to "Not allowed",
            "ru" to "Запрещено"
        ),
        "accessibility_label" to mapOf(
            "uz" to "Maxsus Imkoniyatlar (Accessibility)",
            "en" to "Accessibility Service",
            "ru" to "Спец. возможности (Accessibility)"
        ),
        "accessibility_active" to mapOf(
            "uz" to "FAOL (Oynalarni nazorat qiladi)",
            "en" to "ACTIVE (Monitors & closes UI)",
            "ru" to "АКТИВЕН (Управляет экраном)"
        ),
        "accessibility_inactive" to mapOf(
            "uz" to "Nofaol",
            "en" to "Inactive",
            "ru" to "Неактивен"
        ),
        "adb_conn_label" to mapOf(
            "uz" to "ADB / Root Bog'lanish",
            "en" to "ADB / Root Connection",
            "ru" to "Подключение ADB / Root"
        ),
        "adb_conn_active" to mapOf(
            "uz" to "ULANGAN (100% Avtomat)",
            "en" to "CONNECTED (Full Auto)",
            "ru" to "ПОДКЛЮЧЕНО (Авто)"
        ),
        "adb_conn_inactive" to mapOf(
            "uz" to "Ulanmagan",
            "en" to "Disconnected",
            "ru" to "Отключено"
        ),
        "cm_tab_devices" to mapOf(
            "uz" to "Qurilmalar",
            "en" to "Devices",
            "ru" to "Устройства"
        ),
        "cm_tab_broadcast" to mapOf(
            "uz" to "Buyruq Yuborish",
            "en" to "Broadcast",
            "ru" to "Рассылка команд"
        ),
        "cm_tab_network" to mapOf(
            "uz" to "Tarmoq Skanerlash",
            "en" to "Network Scan",
            "ru" to "Поиск в сети"
        ),
        "cm_tab_pairing" to mapOf(
            "uz" to "Juftlash Yordamchisi",
            "en" to "Pairing Guide",
            "ru" to "Сопряжение"
        ),
        "cm_parallel_broadcast_title" to mapOf(
            "uz" to "PARALLEL BUYRUQ YUBORISH",
            "en" to "PARALLEL COMMAND BROADCAST",
            "ru" to "ПАРАЛЛЕЛЬНАЯ РАССЫЛКА КОМАНД"
        ),
        "cm_comparison_table_title" to mapOf(
            "uz" to "QURILMALARNI TAQQOSLASH JADVALI",
            "en" to "MULTI-DEVICE COMPARISON TABLE",
            "ru" to "ТАБЛИЦА СРАВНЕНИЯ УСТРОЙСТВ"
        ),
        "cm_add_device_title" to mapOf(
            "uz" to "Yangi ADB Qurilma Qo'shish",
            "en" to "Add New ADB Device",
            "ru" to "Добавить новое ADB устройство"
        ),
        "cm_device_name_label" to mapOf(
            "uz" to "Qurilma Nomi",
            "en" to "Device Name",
            "ru" to "Имя устройства"
        ),
        "cm_device_model_label" to mapOf(
            "uz" to "Qurilma Modeli",
            "en" to "Device Model",
            "ru" to "Модель устройства"
        ),
        "cm_ip_address_label" to mapOf(
            "uz" to "IP Manzil",
            "en" to "IP Address",
            "ru" to "IP-адрес"
        ),
        "cm_port_label" to mapOf(
            "uz" to "Port (5555 yoki Ulanish porti)",
            "en" to "Port (5555 or Wireless Port)",
            "ru" to "Порт (5555 или порт подключения)"
        ),
        "cm_btn_add" to mapOf(
            "uz" to "Qo'shish",
            "en" to "Add Device",
            "ru" to "Добавить"
        ),
        "cm_btn_cancel" to mapOf(
            "uz" to "Bekor qilish",
            "en" to "Cancel",
            "ru" to "Отмена"
        ),
        "cm_btn_run" to mapOf(
            "uz" to "Ijro etish ⚡",
            "en" to "Run ⚡",
            "ru" to "Выполнить ⚡"
        ),
        "cm_btn_copy_output" to mapOf(
            "uz" to "Natijani nusxalash",
            "en" to "Copy Output",
            "ru" to "Копировать вывод"
        ),
        "cm_targets_count" to mapOf(
            "uz" to "ta nishon",
            "en" to "targets",
            "ru" to "целей"
        ),
        "cm_primary_badge" to mapOf(
            "uz" to "ASOSIY",
            "en" to "PRIMARY",
            "ru" to "ОСНОВНОЙ"
        ),
        "cm_status_connected" to mapOf(
            "uz" to "Ulangan",
            "en" to "Connected",
            "ru" to "Подключено"
        ),
        "cm_status_connecting" to mapOf(
            "uz" to "Ulanmoqda",
            "en" to "Connecting",
            "ru" to "Подключение"
        ),
        "cm_status_disconnected" to mapOf(
            "uz" to "Uzilgan",
            "en" to "Disconnected",
            "ru" to "Отключено"
        ),
        "cm_status_unauthorized" to mapOf(
            "uz" to "Ruxsatsiz",
            "en" to "Unauthorized",
            "ru" to "Не авторизовано"
        ),
        "cm_status_error" to mapOf(
            "uz" to "Xatolik",
            "en" to "Error",
            "ru" to "Ошибка"
        )
    )

    fun get(key: String, lang: String): String {
        return translations[key]?.get(lang) ?: translations[key]?.get("uz") ?: key
    }

    fun localizeDeviceLabel(label: String, lang: String): String {
        return when (label.trim()) {
            "Ulanish Holati" -> when (lang) {
                "en" -> "Connection Status"
                "ru" -> "Статус подключения"
                else -> "Ulanish Holati"
            }
            "Qurilma Modeli" -> when (lang) {
                "en" -> "Device Model"
                "ru" -> "Модель устройства"
                else -> "Qurilma Modeli"
            }
            "Brend" -> when (lang) {
                "en" -> "Brand"
                "ru" -> "Бренд"
                else -> "Brend"
            }
            "Android Talqin" -> when (lang) {
                "en" -> "Android Version"
                "ru" -> "Версия Android"
                else -> "Android Talqin"
            }
            "SDK Level (API)" -> when (lang) {
                "en" -> "SDK Level (API)"
                "ru" -> "Уровень SDK (API)"
                else -> "SDK Level (API)"
            }
            "Ekran Rezolyutsiyasi" -> when (lang) {
                "en" -> "Screen Resolution"
                "ru" -> "Разрешение экрана"
                else -> "Ekran Rezolyutsiyasi"
            }
            "Ekran Zichligi" -> when (lang) {
                "en" -> "Screen Density"
                "ru" -> "Плотность экрана"
                else -> "Ekran Zichligi"
            }
            "Ekran Yangilanish Tezligi" -> when (lang) {
                "en" -> "Screen Refresh Rate"
                "ru" -> "Частота обновления"
                else -> "Ekran Yangilanish Tezligi"
            }
            "Protsessor (Board)" -> when (lang) {
                "en" -> "Processor (Board)"
                "ru" -> "Процессор (Board)"
                else -> "Protsessor (Board)"
            }
            "Ishlab Chiqaruvchi" -> when (lang) {
                "en" -> "Manufacturer"
                "ru" -> "Производитель"
                else -> "Ishlab Chiqaruvchi"
            }
            "CPU Tizim Platformasi" -> when (lang) {
                "en" -> "CPU Platform"
                "ru" -> "Платформа CPU"
                else -> "CPU Tizim Platformasi"
            }
            "Yadro Versiyasi" -> when (lang) {
                "en" -> "Kernel Version"
                "ru" -> "Версия ядра"
                else -> "Yadro Versiyasi"
            }
            "Tizim Standart Tili" -> when (lang) {
                "en" -> "System Language"
                "ru" -> "Язык системы"
                else -> "Tizim Standart Tili"
            }
            "Operativ RAM Xotira" -> when (lang) {
                "en" -> "RAM Memory"
                "ru" -> "Оперативная память"
                else -> "Operativ RAM Xotira"
            }
            "Batareya Zaryadi" -> when (lang) {
                "en" -> "Battery Level"
                "ru" -> "Заряд батареи"
                else -> "Batareya Zaryadi"
            }
            "Batareya Sog'ligi" -> when (lang) {
                "en" -> "Battery Health"
                "ru" -> "Состояние батареи"
                else -> "Batareya Sog'ligi"
            }
            "Batareya Harorati" -> when (lang) {
                "en" -> "Battery Temperature"
                "ru" -> "Температура батареи"
                else -> "Batareya Harorati"
            }
            "Batareya Kuchlanishi" -> when (lang) {
                "en" -> "Battery Voltage"
                "ru" -> "Напряжение батареи"
                else -> "Batareya Kuchlanishi"
            }
            "Ichki Hamkor Xotira" -> when (lang) {
                "en" -> "Internal Storage"
                "ru" -> "Внутренняя память"
                else -> "Ichki Hamkor Xotira"
            }
            "SELinux Xavfsizligi" -> when (lang) {
                "en" -> "SELinux Security"
                "ru" -> "Безопасность SELinux"
                else -> "SELinux Xavfsizligi"
            }
            else -> label
        }
    }

    fun localizeDeviceValue(value: String, lang: String): String {
        val trimmedValue = value.trim()
        if (trimmedValue == "Wireless ADB (Ulangan)") {
            return when (lang) {
                "en" -> "Wireless ADB (Connected)"
                "ru" -> "Wireless ADB (Подключено)"
                else -> "Wireless ADB (Ulangan)"
            }
        }
        if (trimmedValue == "Mahalliy rejim (ADB ulanmagan)") {
            return when (lang) {
                "en" -> "Local mode (ADB disconnected)"
                "ru" -> "Локальный режим (ADB не подключен)"
                else -> "Mahalliy rejim (ADB ulanmagan)"
            }
        }
        if (trimmedValue == "Standart/Avto") {
            return when (lang) {
                "en" -> "Standard/Auto"
                "ru" -> "Стандарт/Авто"
                else -> "Standart/Avto"
            }
        }
        if (trimmedValue == "Yaxshi (Good)") {
            return when (lang) {
                "en" -> "Good"
                "ru" -> "Хорошее (Good)"
                else -> "Yaxshi (Good)"
            }
        }
        if (trimmedValue == "Qizib ketgan (Overheat)") {
            return when (lang) {
                "en" -> "Overheated"
                "ru" -> "Перегрев"
                else -> "Qizib ketgan (Overheat)"
            }
        }
        if (trimmedValue == "Yaroqsiz (Dead)") {
            return when (lang) {
                "en" -> "Dead"
                "ru" -> "Неисправна"
                else -> "Yaroqsiz (Dead)"
            }
        }
        if (trimmedValue == "Kuchlanish yuqori (Over Voltage)") {
            return when (lang) {
                "en" -> "Over Voltage"
                "ru" -> "Перенапряжение"
                else -> "Kuchlanish yuqori (Over Voltage)"
            }
        }
        if (trimmedValue == "Sovuq (Cold)") {
            return when (lang) {
                "en" -> "Cold"
                "ru" -> "Холодная"
                else -> "Sovuq (Cold)"
            }
        }
        if (trimmedValue == "Noma'lum") {
            return when (lang) {
                "en" -> "Unknown"
                "ru" -> "Неизвестно"
                else -> "Noma'lum"
            }
        }
        // "Umumiy: 24G (Bo'sh: 8.7G)"
        if (trimmedValue.startsWith("Umumiy: ")) {
            val total = trimmedValue.substringAfter("Umumiy: ").substringBefore(" (Bo'sh: ")
            val free = trimmedValue.substringAfter(" (Bo'sh: ").substringBefore(")")
            return when (lang) {
                "en" -> "Total: $total (Free: $free)"
                "ru" -> "Всего: $total (Свободно: $free)"
                else -> trimmedValue
            }
        }
        // "2.75 GB (Bo'sh: 0.51 GB)" or general "(Bo'sh: " format
        if (trimmedValue.contains(" (Bo'sh: ")) {
            val before = trimmedValue.substringBefore(" (Bo'sh: ")
            val inside = trimmedValue.substringAfter(" (Bo'sh: ").substringBefore(")")
            return when (lang) {
                "en" -> "$before (Free: $inside)"
                "ru" -> "$before (Свободно: $inside)"
                else -> trimmedValue
            }
        }
        return value
    }

    fun getLocalizedScript(script: ScriptEntity, lang: String): ScriptEntity {
        return when (script.command.trim()) {
            "settings put secure user_refresh_rate 120" -> script.copy(
                title = when (lang) {
                    "uz" -> "Ekran yangilanish tezligi 120Hz"
                    "ru" -> "Частота обновления экрана 120 Гц"
                    else -> "Force Screen Refresh Rate 120Hz"
                },
                description = when (lang) {
                    "uz" -> "Tizim ekran yangilanish tezligini majburiy 120Hz ga sozlaydi, bu animatsiyalarni ancha silliq qiladi."
                    "ru" -> "Принудительно устанавливает частоту обновления экрана на 120 Гц для плавной анимации."
                    else -> "Forces system screen refresh rate to 120Hz, making scrolling and animations silky smooth."
                },
                category = when (lang) {
                    "uz" -> "Ekran"
                    "ru" -> "Экран"
                    else -> "Screen"
                }
            )
            "pm list packages -3" -> script.copy(
                title = when (lang) {
                    "uz" -> "Uchinchi xonadon ilovalarini ro'yxatlash"
                    "ru" -> "Список установленных приложений"
                    else -> "List Third-Party Apps"
                },
                description = when (lang) {
                    "uz" -> "Faqat siz o'rnatgan tashqi ilovalar ro'yxatini ko'rsatadi."
                    "ru" -> "Показывает только установленные пользователем сторонние приложения."
                    else -> "Lists only user-installed third-party packages."
                },
                category = when (lang) {
                    "uz" -> "Ilova"
                    "ru" -> "Система"
                    else -> "System"
                }
            )
            "dumpsys battery" -> script.copy(
                title = when (lang) {
                    "uz" -> "Batareya diagnostikasi"
                    "ru" -> "Диагностика батареи"
                    else -> "Battery Diagnostics"
                },
                description = when (lang) {
                    "uz" -> "Telefon batareyasi holati, harorati va voltaj ko'rsatkichlarini ko'rsatish."
                    "ru" -> "Отображает полную информацию о состоянии батареи, заряде и здоровье."
                    else -> "Dumps comprehensive details of cellular battery state, temperature, voltage, and health metrics."
                },
                category = when (lang) {
                    "uz" -> "Tizim"
                    "ru" -> "Система"
                    else -> "System"
                }
            )
            "wm density 440" -> script.copy(
                title = when (lang) {
                    "uz" -> "DPI zichligini 440 ga sozlash"
                    "ru" -> "Изменение плотности DPI на 440"
                    else -> "Set Custom DPI (440 Density)"
                },
                description = when (lang) {
                    "uz" -> "Ekran elementlarini ixchamroq qilish uchun zichlikni 440 dpi ga sozlash."
                    "ru" -> "Устанавливает плотность экрана на 440 DPI для уменьшения элементов. (Для сброса: wm density reset)"
                    else -> "Sets viewport pixel density to 440 DPI. (To restore default: wm density reset)"
                },
                category = when (lang) {
                    "uz" -> "Ekran"
                    "ru" -> "Экран"
                    else -> "Screen"
                }
            )
            "settings put global window_animation_scale 0.5 && settings put global transition_animation_scale 0.5 && settings put global animator_duration_scale 0.5" -> script.copy(
                title = when (lang) {
                    "uz" -> "Animatsiya tezligini oshirish (0.5x)"
                    "ru" -> "Ускорение анимаций (0.5x)"
                    else -> "Speed Up Animation Scales (0.5x)"
                },
                description = when (lang) {
                    "uz" -> "Sizning telefon interfeysingizni 2 marta tezkor ishlaydigan qiladi."
                    "ru" -> "Увеличивает скорость интерфейса устройства в 2 раза за счет анимаций."
                    else -> "Cuts animation latency in half to accelerate transitions and window-drawing responsiveness."
                },
                category = when (lang) {
                    "uz" -> "Tizim"
                    "ru" -> "Система"
                    else -> "System"
                }
            )
            "top -m 5 -n 1" -> script.copy(
                title = when (lang) {
                    "uz" -> "CPU unumdorligi (Top 5 protsess)"
                    "ru" -> "Топ 5 процессов CPU"
                    else -> "CPU Performance (Top 5 Processes)"
                },
                description = when (lang) {
                    "uz" -> "Eng ko'p protsessor resursini iste'mol qilayotgan 5 ta faol dasturni ko'rsatish."
                    "ru" -> "Отображает 5 наиболее ресурсоемких активных процессов процессора."
                    else -> "Polls device and returns the top 5 active CPU resource-consuming processes."
                },
                category = when (lang) {
                    "uz" -> "Tizim"
                    "ru" -> "Система"
                    else -> "System"
                }
            )
            "cat /proc/meminfo" -> script.copy(
                title = when (lang) {
                    "uz" -> "Xotira holati (Meminfo)"
                    "ru" -> "Информация о памяти (Meminfo)"
                    else -> "Show Memory Stats (Meminfo)"
                },
                description = when (lang) {
                    "uz" -> "Tizim tezkor xotirasi (RAM) jurnali va uning taqsimoti jadvali."
                    "ru" -> "Показывает подробный лог состояния системной оперативной памяти."
                    else -> "Prints low-level kernel reports containing total memory sizes and free byte mappings."
                },
                category = when (lang) {
                    "uz" -> "Tizim"
                    "ru" -> "Система"
                    else -> "System"
                }
            )
            "df -h /data" -> script.copy(
                title = when (lang) {
                    "uz" -> "Disk xotirasi xaritasi (df -h)"
                    "ru" -> "Дисковое пространство (df -h)"
                    else -> "Disk Resource Usage (df -h)"
                },
                description = when (lang) {
                    "uz" -> "Ichki xotira (data bo'limi) umumiy, bo'sh va band hajmi."
                    "ru" -> "Отображает заполненность внутренней памяти в удобном формате."
                    else -> "Inspects user storage partitions and reads total size, human-readable free space, and directories."
                },
                category = when (lang) {
                    "uz" -> "Tizim"
                    "ru" -> "Система"
                    else -> "System"
                }
            )
            "pm trim-caches 4G" -> script.copy(
                title = when (lang) {
                    "uz" -> "Kesh xotirani optimallash (Tozalash)"
                    "ru" -> "Очистка системного кэша"
                    else -> "Trim Inactive Caches"
                },
                description = when (lang) {
                    "uz" -> "Fon ilovalar kesh fayllarini majburiy tozalaydi, bo'sh joy yaratadi."
                    "ru" -> "Принудительно очищает неактивный кэш фоновых приложений на 4 ГБ."
                    else -> "Forces Android to purge up to 4GB of orphan package cache blocks to reclaim active space."
                },
                category = when (lang) {
                    "uz" -> "Tizim"
                    "ru" -> "Система"
                    else -> "System"
                }
            )
            "svc power stayon true" -> script.copy(
                title = when (lang) {
                    "uz" -> "Quvvatda ekranni doim uzoq yoniq tutish"
                    "ru" -> "Держать экран включенным при зарядке"
                    else -> "Keep Screen Awake (Plugged-In)"
                },
                description = when (lang) {
                    "uz" -> "Zaryad olayotganda telefon ekrani o'chib qolmasligini faollashtiradi."
                    "ru" -> "Предотвращает отключение экрана во время зарядки."
                    else -> "Instructs Android power managers to keep screen powered while device resides on zaryad."
                },
                category = when (lang) {
                    "uz" -> "Tizim"
                    "ru" -> "Система"
                    else -> "System"
                }
            )
            else -> script
        }
    }
}
