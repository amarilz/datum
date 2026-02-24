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
    implementation("org.postgresql:postgresql:42.7.10")
}
