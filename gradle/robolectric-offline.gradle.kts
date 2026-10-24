// Robolectric no descarga su Android en tiempo de prueba (falla tras un proxy o antivirus que intercepta
// HTTPS): lo baja Gradle y Robolectric lo lee sin red. Si cambias @Config(sdk) en las pruebas, cambia
// también esta versión (la tabla está en https://github.com/robolectric/robolectric/releases).
val robolectricSdk: Configuration = configurations.create("robolectricSdk")

dependencies {
    add(robolectricSdk.name, "org.robolectric:android-all-instrumented:15-robolectric-13954326-i7") { isTransitive = false }
}

val robolectricJars = tasks.register<Sync>("robolectricJars") {
    from(robolectricSdk)
    into(layout.buildDirectory.dir("robolectric"))
}

tasks.withType<Test>().configureEach {
    dependsOn(robolectricJars)
    systemProperty("robolectric.offline", "true")
    systemProperty("robolectric.dependency.dir", layout.buildDirectory.dir("robolectric").get().asFile.absolutePath)
}
