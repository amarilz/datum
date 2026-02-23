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
}
