dependencies {
    "api"(project(":common"))
    "implementation"("io.ktor:ktor-client-core:3.0.3")
    "implementation"("io.ktor:ktor-client-cio:3.0.3")
    "implementation"("io.ktor:ktor-client-websockets:3.0.3")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "org.feuyeux.ws.client.WsClientKt"
    }
    from({
        configurations["runtimeClasspath"].filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.register<JavaExec>("run") {
    mainClass.set("org.feuyeux.ws.client.WsClientKt")
    classpath = sourceSets["main"].runtimeClasspath
}
