// AGP's transitive build tools are not app runtime dependencies. Declare their
// patched versions explicitly so Gradle resolves them and Dependabot can update them.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.google.guava:guava:33.7.2-jre")
        classpath("org.apache.commons:commons-lang3:3.21.0")
        classpath("org.apache.httpcomponents:httpclient:4.5.14")
        classpath("org.bitbucket.b_c:jose4j:0.9.7")
        classpath("org.bouncycastle:bcpkix-jdk18on:1.86")
        classpath("org.bouncycastle:bcprov-jdk18on:1.86")
        classpath("org.bouncycastle:bcutil-jdk18on:1.86")
        classpath("org.jdom:jdom2:2.0.6.1")
    }
}

plugins {
    id("com.android.application") version "9.4.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.21" apply false
}
