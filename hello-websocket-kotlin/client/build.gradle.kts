plugins {
    application
}

dependencies {
    "api"(project(":common"))
    "implementation"("io.ktor:ktor-client-core:3.5.1")
    "implementation"("io.ktor:ktor-client-cio:3.5.1")
    "implementation"("io.ktor:ktor-client-websockets:3.5.1")
}

application {
    mainClass.set("org.feuyeux.ws.client.WsClientKt")
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