plugins {
    application
}

base {
    archivesName.set("datum-cli")
}

application {
    mainClass.set("com.amarildoaliaj.datum.cli.Main")
}

dependencies {
    implementation(project(":core"))

    implementation("info.picocli:picocli:4.7.7")
    annotationProcessor("info.picocli:picocli-codegen:4.7.7")

    implementation("org.postgresql:postgresql:42.7.10")
}
