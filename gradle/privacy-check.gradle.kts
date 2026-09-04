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

    /**
     * MUHIM: **birlashtirilgan** manifest tekshiriladi, manba emas.
     *
     * F3 da aniqlandi: `onnxruntime-android` o'z manifestida INTERNET,
     * ACCESS_NETWORK_STATE va telemetriya provayderini e'lon qiladi. Ular
     * birlashtirishda APK'ga kirib qolgan edi, manba manifestimizda esa
     * yo'q. Faqat manbani tekshirgan versiya "o'tdi" deb aytardi —
     * ya'ni tekshiruvning o'zi yolg'on xotirjamlik bergan.
     */
    val mergedManifests = project.fileTree(project.layout.buildDirectory) {
        include("intermediates/merged_manifest*/**/AndroidManifest.xml")
        include("intermediates/merged_manifests/**/AndroidManifest.xml")
    }
    val manifestFile = layout.projectDirectory.file("src/main/AndroidManifest.xml")
    val sourceRoot = project.rootDir
    val sources = project.fileTree(sourceRoot) {
        include("**/*.kt")
        exclude("**/build/**")
    }

    inputs.file(manifestFile)
    inputs.files(sources)
    inputs.files(mergedManifests).optional()

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

        fun checkManifest(text: String, label: String) {
            forbiddenPermissions.forEach { perm ->
                // tools:node="remove" bilan olib tashlanganlari muammo emas
                val declared = Regex(
                    """<uses-permission(?![^>]*tools:node\s*=\s*"remove")[^>]*android:name\s*=\s*"$perm""""
                )
                if (declared.containsMatchIn(text)) {
                    problems += "$label: taqiqlangan ruxsat e'lon qilingan: $perm"
                }
            }
        }

        checkManifest(manifestFile.asFile.readText(), "Manba manifest")

        val merged = mergedManifests.files.filter { it.isFile }
        if (merged.isEmpty()) {
            logger.warn(
                "DIQQAT: birlashtirilgan manifest topilmadi. To'liq tekshiruv uchun " +
                    "avval `assembleDebug` yoki `assembleRelease` bajaring."
            )
        }
        merged.forEach { f ->
            checkManifest(f.readText(), "BIRLASHTIRILGAN manifest (${f.parentFile.name})")
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

/**
 * Manifest birlashtirilgandan KEYIN ishlashi shart.
 *
 * Aks holda `build` ichida u eski (yoki umuman yo'q) birlashtirilgan
 * manifestni ko'radi va natija ishonchsiz bo'ladi.
 */
tasks.named("verifyPrivacy") {
    // processDebugMainManifest, processReleaseMainManifest VA ABI bo'yicha
    // processDebugManifest / processReleaseManifest — hammasi kerak.
    dependsOn(tasks.matching { it.name.matches(Regex("^process[A-Z].*Manifest$")) })
}

tasks.named("check") { dependsOn("verifyPrivacy") }
