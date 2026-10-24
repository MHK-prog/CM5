# CM 5.0

اپ اندرویدی مستقل برای مدیریت کلاس‌ها و صدور کارنامه‌ی زبان‌آموزان. رابط داخل APK بسته‌بندی شده و برای استفاده به سایت یا اتصال اینترنت نیاز ندارد.

## ساخت APK در ویندوز

1. JDK 17 یا جدیدتر و Android SDK نصب باشند؛ مسیر SDK در `local.properties` تنظیم شود (این فایل در Git قرار نمی‌گیرد). در این رایانه، `JAVA_HOME` باید به JDK 27 نصب‌شده اشاره کند.
2. در PowerShell از ریشه‌ی پروژه اجرا کنید:

   ```powershell
   $env:JAVA_HOME = 'C:\Program Files\Java\jdk-27'
   .\gradlew.bat assembleDebug
   ```

3. APK آزمایشی در `app\build\outputs\apk\debug\app-debug.apk` ساخته می‌شود. خروجی release در `app\build\outputs\apk\release\` قرار می‌گیرد.

گردش‌کار GitHub Actions نیز بعد از push، APK آزمایشی را به‌عنوان artifact در اجرای workflow قرار می‌دهد.

## ساخت نسخه‌ی امضاشده در GitHub

فایل keystore را به مخزن اضافه نکنید. آن را به Base64 تبدیل و در **Settings → Secrets and variables → Actions** به‌عنوان این چهار Repository Secret ذخیره کنید:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

سپس از **Actions → Build signed CM 5.0 release APK → Run workflow** گردش‌کار انتشار را دستی اجرا کنید. APK امضاشده در artifact همان اجرا قرار می‌گیرد. اگر فایل کلید Base64 نشده است، PowerShell ویندوز:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\path\to\release-key.jks")) | Set-Clipboard
```

## داده‌ها

اطلاعات برنامه روی دستگاه ذخیره می‌شود. از صفحه‌ی پشتیبان‌گیری می‌توان JSON خروجی گرفت یا فایل پشتیبان CM 4.5.2 را وارد کرد. اطلاعات نمرات روزانه و توضیح سوپروایزر وارد CM 5.0 نمی‌شوند.
