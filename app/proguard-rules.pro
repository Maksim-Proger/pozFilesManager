# jcifs-ng (SMB) создаёт часть классов по имени — их нельзя переименовывать
-keep class jcifs.** { *; }
-dontwarn jcifs.**
-dontwarn javax.servlet.**
-dontwarn org.slf4j.**
-dontwarn org.bouncycastle.**
-keep class org.bouncycastle.jcajce.provider.** { *; }
-keep class org.bouncycastle.crypto.digests.** { *; }
