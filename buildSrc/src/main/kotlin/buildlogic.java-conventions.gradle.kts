plugins {
    `java-library`
}

repositories {
    maven("https://repo.maven.apache.org/maven2/")
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
    maven("https://repo.papermc.io/repository/maven-public/")
    // Opt-in local repository usage; keeping it disabled by default avoids
    // Gradle resolving partial metadata/artifacts from ~/.m2 and getting stuck there.
    if (providers.gradleProperty("useMavenLocal").orNull == "true") {
        mavenLocal()
    }
}

group = "net.countercraft"
version = "8.0.0_beta-6"

tasks.withType<JavaCompile>() {
    options.encoding = "UTF-8"
}

tasks.withType<Javadoc>() {
    options.encoding = "UTF-8"
}
