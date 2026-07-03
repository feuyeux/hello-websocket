dependencies {
    "api"(project(":common"))
    "implementation"("io.ktor:ktor-server-core:3.0.3")
    "implementation"("io.ktor:ktor-server-netty:3.0.3")
    "implementation"("io.ktor:ktor-server-websockets:3.0.3")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "org.feuyeux.ws.server.WsServerKt"
    }
    from({
        configurations["runtimeClasspath"].filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.register<JavaExec>("run") {
    mainClass.set("org.feuyeux.ws.server.WsServerKt")
    classpath = sourceSets["main"].runtimeClasspath
}
