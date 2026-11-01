# SQLCipher ships native code reached via JNI.
-keep class net.zetetic.database.** { *; }
-keep class net.sqlcipher.** { *; }

# Room generates implementations reflectively at build time.
-keep class * extends androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.paging.**

# Entities are read via reflection by Room's PropertyValueFactory equivalents.
-keep class com.passvault.data.PasswordEntry { *; }
