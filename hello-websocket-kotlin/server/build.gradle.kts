plugins {
    application
}

dependencies {
    "api"(project(":common"))
    "implementation"("io.ktor:ktor-server-core:3.5.1")
    "implementation"("io.ktor:ktor-server-netty:3.5.1")
    "implementation"("io.ktor:ktor-server-websockets:3.5.1")
}

application {
    mainClass.set("org.feuyeux.ws.server.WsServerKt")
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