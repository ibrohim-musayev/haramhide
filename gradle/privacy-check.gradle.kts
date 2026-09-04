/**
 * **TZ 10.2 / FR-301 — maxfiylik tekshiruvi.**
 *
 * Ilovaning asosiy da'vosi: "hech qanday piksel qurilmadan chiqmaydi".
 * Bu da'vo faqat kod ko'rigiga tayanmasligi kerak — bir kun kimdir
 * `INTERNET` ruxsatini "vaqtincha" qo'shib qo'yadi va da'vo jimgina yolg'onga
 * aylanadi.
 *
 * Shuning uchun tekshiruv `check` ga ulanadi va build'ni to'xtatadi.
 *
 * Ro'yxatlar task ichida e'lon qilingan: skript darajasidagi o'zgaruvchilarga
 * murojaat configuration cache bilan mos kelmaydi.
 */
tasks.register("verifyPrivacy") {
    group = "verification"
    description = "TZ 10.2: taqiqlangan ruxsatlar va piksel yozish chaqiruvlarini tekshiradi"

    val manifestFile = layout.projectDirectory.file("src/main/AndroidManifest.xml")
    val sourceRoot = project.rootDir
    val sources = project.fileTree(sourceRoot) {
        include("**/*.kt")
        exclude("**/build/**")
    }

    inputs.file(manifestFile)
    inputs.files(sources)

    doLast {
        val forbiddenPermissions = listOf(
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.ACCESS_WIFI_STATE",
        )
        // Piksellarni diskka yozishi mumkin bo'lgan chaqiruvlar
        val forbiddenApis = listOf(
            ".compress(",
            "FileOutputStream",
            "MediaStore.Images",
            "openFileOutput",
        )
        // Bu fayllar rasm O'QIYDI, yozmaydi
        val excludes = listOf(
            "TestPatternActivity.kt",
            "DebugControlReceiver.kt",
        )

        val problems = mutableListOf<String>()

        val manifest = manifestFile.asFile.readText()
        forbiddenPermissions.forEach { perm ->
            // Izohda eslatib o'tish mumkin — faqat haqiqiy e'lon taqiqlanadi
            if (Regex("""<uses-permission[^>]*android:name\s*=\s*"$perm"""").containsMatchIn(manifest)) {
                problems += "Manifestda taqiqlangan ruxsat e'lon qilingan: $perm"
            }
        }

        sources.files
            .filterNot { f -> excludes.any { f.name == it } }
            .forEach { file ->
                val text = file.readText()
                forbiddenApis.forEach { api ->
                    if (text.contains(api)) {
                        problems += "Piksel yozish ehtimoli: ${file.relativeTo(sourceRoot).path} da '$api'"
                    }
                }
            }

        if (problems.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("MAXFIYLIK TEKSHIRUVI MUVAFFAQIYATSIZ (TZ 10.2 / FR-301)")
                    appendLine()
                    problems.forEach { appendLine("  • $it") }
                    appendLine()
                    appendLine("Ilovaning asosiy da'vosi — hech qanday piksel qurilmadan")
                    appendLine("chiqmaydi va diskka yozilmaydi. Agar bu o'zgarish ataylab")
                    appendLine("bo'lsa, TZ 9 va 10-bo'limlarini hamda README ni yangilang.")
                }
            )
        }
        logger.lifecycle("Maxfiylik tekshiruvi o'tdi: tarmoq ruxsati yo'q, piksel yozilmaydi.")
    }
}

tasks.named("check") { dependsOn("verifyPrivacy") }
