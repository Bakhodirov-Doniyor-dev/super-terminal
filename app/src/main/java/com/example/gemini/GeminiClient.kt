package com.example.gemini

import android.os.SystemClock
import android.util.Log
import com.example.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

object GeminiClient {
    private const val TAG = "GeminiClient"
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    data class GeminiModel(val id: String, val displayName: String, val badge: String = "", val description: String = "")
    
    val AVAILABLE_MODELS = listOf(
        GeminiModel("gemini-3.6-flash", "Gemini 3.6 Flash", "FAST", "Asosiy va tezkor yordamchi"),
        GeminiModel("gemini-3.5-flash-lite", "Gemini 3.5 Flash-Lite", "LITE", "Eng tezkor javoblar"),
        GeminiModel("gemini-3.1-pro-preview", "Gemini 3.1 Pro", "PRO", "Murakkab fikrlash va kodlash")
    )
    
    const val DEFAULT_MODEL_ID = "gemini-3.6-flash"

    data class GeminiResponse(
        val text: String,
        val toolCalls: List<ToolCall>,
        val shouldFallback: Boolean = false,
        val actualModelId: String? = null
    )

    data class ToolCall(
        val name: String,
        val args: Map<String, Any>
    )

    suspend fun generateContent(
        prompt: String,
        chatHistory: List<Pair<String, String>> = emptyList(),
        currentLanguage: String = "uz",
        deviceInfo: String = "",
        attachedFiles: List<AttachedFileData> = emptyList(),
        modelId: String = DEFAULT_MODEL_ID,
        onUpdate: (String) -> Unit = {}
    ): GeminiResponse {
        // Barcha mavjud modellarni navbat bilan sinash (foydalanuvchi tanlagan model birinchi, keyin qolganlari)
        val allModelIds = AVAILABLE_MODELS.map { it.id }
        val modelsToTry = listOf(modelId) + (allModelIds - modelId)

        var lastResponse: GeminiResponse? = null

        for ((index, currentModel) in modelsToTry.withIndex()) {
            if (index > 0) {
                kotlinx.coroutines.delay(1000) // Zaxira modelga o'tishdan oldin 1 soniya kutish
                Log.w(TAG, "Model ${modelsToTry[index - 1]} limitga uchradi. Zaxira modelga o'tilmoqda: $currentModel.")
            }
            val response = generateContentForModel(currentModel, prompt, chatHistory, currentLanguage, deviceInfo, attachedFiles, onUpdate)
            if (!response.shouldFallback) {
                return response
            }
            lastResponse = response
        }

        return lastResponse ?: GeminiResponse(
            text = when (currentLanguage) {
                "uz" -> "Barcha AI modellarida limit yoki xatolik yuz berdi. Iltimos, birozdan so'ng qayta urinib ko'ring."
                "ru" -> "Ошибка во всех моделях ИИ. Пожалуйста, попробуйте позже."
                else -> "Error across all AI models. Please try again later."
            },
            toolCalls = emptyList()
        )
    }

    private suspend fun generateContentForModel(
        modelName: String,
        prompt: String,
        chatHistory: List<Pair<String, String>>,
        currentLanguage: String,
        deviceInfo: String,
        attachedFiles: List<AttachedFileData> = emptyList(),
        onUpdate: (String) -> Unit = {}
    ): GeminiResponse = withContext(Dispatchers.IO) {
        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            ""
        }

        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext GeminiResponse(
                text = when (currentLanguage) {
                    "uz" -> "Xatolik: Gemini API kaliti kiritilmagan. Iltimos, AI Studio sozlamalarida GEMINI_API_KEY kalitini kiriting."
                    "ru" -> "Ошибка: API-ключ Gemini не настроен. Пожалуйста, укажите GEMINI_API_KEY в настройках AI Studio."
                    else -> "Error: Gemini API key is missing. Please set GEMINI_API_KEY in the AI Studio secrets."
                },
                toolCalls = emptyList()
            )
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:streamGenerateContent?alt=sse&key=$apiKey"

        try {
            // Build the json body using native JSONObject
            val root = JSONObject()

            // Contents array
            val contentsArray = JSONArray()

            // Add history if present
            for (history in chatHistory) {
                val histUserObj = JSONObject()
                histUserObj.put("role", "user")
                val histUserParts = JSONArray()
                histUserParts.put(JSONObject().put("text", history.first))
                histUserObj.put("parts", histUserParts)
                contentsArray.put(histUserObj)

                val histModelObj = JSONObject()
                histModelObj.put("role", "model")
                val histModelParts = JSONArray()
                histModelParts.put(JSONObject().put("text", history.second))
                histModelObj.put("parts", histModelParts)
                contentsArray.put(histModelObj)
            }

            // Add the current prompt
            val currentUserObj = JSONObject()
            currentUserObj.put("role", "user")
            val currentUserParts = JSONArray()

            // First, add inlineData parts (for images or binary documents)
            for (file in attachedFiles) {
                if (file.base64Data != null && file.mimeType != null) {
                    val inlineDataObj = JSONObject().apply {
                        put("mimeType", file.mimeType)
                        put("data", file.base64Data)
                    }
                    val partObj = JSONObject().apply {
                        put("inlineData", inlineDataObj)
                    }
                    currentUserParts.put(partObj)
                }
            }

            // Construct the final prompt text including any text file contents
            val promptBuilder = java.lang.StringBuilder()
            val textFiles = attachedFiles.filter { it.textContent != null }
            if (textFiles.isNotEmpty()) {
                promptBuilder.append("Attached files for your analysis:\n\n")
                for (file in textFiles) {
                    promptBuilder.append("=== FILE: ${file.name} (${file.mimeType}) ===\n")
                    promptBuilder.append(file.textContent)
                    promptBuilder.append("\n=== END OF FILE ===\n\n")
                }
            }
            promptBuilder.append(prompt)

            currentUserParts.put(JSONObject().put("text", promptBuilder.toString()))
            currentUserObj.put("parts", currentUserParts)
            contentsArray.put(currentUserObj)

            root.put("contents", contentsArray)

            // System Instruction
            val sysInstructionObj = JSONObject()
            val sysInstructionParts = JSONArray()
            val systemText = """
                Siz 'Super ADB & Terminal' Ubuntu OS boshqaruv simulyatori ilovasining professional AI assistentisiz.
                Sening haqiqiy isming va texnik modeling: ${modelName}.
                Agar foydalanuvchi sendan ismingni, modelingni (qaysi modelsan) yoki versiyangni so'rasa, o'zingni albatta aniq qilib "${modelName}" deb tanishtir. Boshqa nomlarni, jumladan Gemini 1.5, 2.5 yoki boshqa modellarni tilga olma, sen aynan ${modelName} san.
                
                Foydalanuvchi o'zbek, rus yoki ingliz tillarida murojaat qiladi. Sizda ilovaning barcha funksiyalarini boshqarish huquqi bor.
                Foydalanuvchi buyrug'iga ko'ra tegishli funksiyani (tool) chaqiring va unga javob bering.
                Javobingiz o'ta qisqa, madaniyatli va foydalanuvchi tilida bo'lsin.
                Siz quyidagi amallarni bajara olasiz:
                - disconnect_adb: ADB ulanishni uzish
                - clear_terminal: Terminalni tozalash
                - toggle_fullscreen: To'liq ekran (immersive) rejimiga o'tish yoki chiqish
                - add_new_tab: Yangi terminal oynasi (tab) qo'shish
                - open_tabs_screen: Oynalar ro'yxatini ko'rsatish
                - toggle_main_menu: Asosiy 3-nuqtali menyuni ochish/yopish
                - run_terminal_command: Terminalda buyruq ishga tushirish (ls, getprop, pm, top, etc.)
                - trigger_voice_input: Ovozli yozishni boshlash
                - toggle_voice_playback: Ovozli ijro (TTS) ni yoqish/o'chirish
                - share_output: Terminal natijalarini ulashish
                - press_virtual_key: Virtual tugmalarni bosish (ESC, TAB, CTRL, UP, DOWN, etc.)
                - toggle_bottom_bar: Pastki menyu panelini yashirish yoki ko'rsatish
                - switch_view: Asosiy ekranlarni almashtirish (0 = Terminal, 1 = Apps, 2 = Monitor, 3 = Files)
                - generate_image: Foydalanuvchi rasm, surat, illyustratsiya yoki tasvir yaratishni so'raganda chaqiring. Promptni ingliz tilida juda batafsil yozing.
                - generate_audio: Foydalanuvchi musiqa, audio, fon kuyi, tabiat tovushlari yoki ohang yaratishni so'raganda chaqiring.
                - generate_video: Foydalanuvchi video, animatsiya yoki klip yaratishni so'raganda chaqiring.
                - create_file: Foydalanuvchi istalgan dasturlash fayli (Python, Shell skript, HTML, JSON, matn, CSV va hk.) yaratishni so'raganda to'liq kodi bilan chaqiring.
                - search_web: Foydalanuvchi internetdan biror ma'lumot, yangilik, fakt, ob-havo, narxlar yoki jonli javoblarni qidirishni so'raganda (yoki savol doimiy o'zgarib turuvchi ma'lumot talab qilsa) chaqiring.
                
                Joriy qurilma ma'lumotlari: $deviceInfo
            """.trimIndent()
            sysInstructionParts.put(JSONObject().put("text", systemText))
            sysInstructionObj.put("parts", sysInstructionParts)
            root.put("systemInstruction", sysInstructionObj)

            // Tools definitions
            val toolsArray = JSONArray()
            val toolObj = JSONObject()
            val functionDeclarations = JSONArray()

            // 1. disconnect_adb
            functionDeclarations.put(JSONObject().apply {
                put("name", "disconnect_adb")
                put("description", "ADB ulanishini uzadi (Kanal uzish).")
            })

            // 2. clear_terminal
            functionDeclarations.put(JSONObject().apply {
                put("name", "clear_terminal")
                put("description", "Terminal ekranini va loglarini tozalaydi.")
            })

            // 3. toggle_fullscreen
            functionDeclarations.put(JSONObject().apply {
                put("name", "toggle_fullscreen")
                put("description", "To'liq ekran (Immersive mode) rejimini yoqadi yoki o'chiradi.")
            })

            // 4. add_new_tab
            functionDeclarations.put(JSONObject().apply {
                put("name", "add_new_tab")
                put("description", "Yangi terminal oynasi (Oyna/Tab) qo'shadi.")
            })

            // 5. open_tabs_screen
            functionDeclarations.put(JSONObject().apply {
                put("name", "open_tabs_screen")
                put("description", "Barcha ochiq oynalarni boshqarish ekranini ochadi.")
            })

            // 6. toggle_main_menu
            functionDeclarations.put(JSONObject().apply {
                put("name", "toggle_main_menu")
                put("description", "Asosiy 3-nuqtali menyuni (Mani Meni) ochadi yoki yopadi.")
            })

            // 7. run_terminal_command
            functionDeclarations.put(JSONObject().apply {
                put("name", "run_terminal_command")
                put("description", "Terminalda linux yoki ADB shell buyrug'ini ishga tushiradi.")
                put("parameters", JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", JSONObject().apply {
                        put("command", JSONObject().apply {
                            put("type", "STRING")
                            put("description", "Ishga tushiriladigan terminal buyrug'i (masalan: 'ls -la', 'neofetch', 'top -m 10').")
                        })
                    })
                    put("required", JSONArray().apply { put("command") })
                })
            })

            // 8. trigger_voice_input
            functionDeclarations.put(JSONObject().apply {
                put("name", "trigger_voice_input")
                put("description", "Ovoz orqali buyruqlarni yozishni boshlaydi (Ovozli kiritish).")
            })

            // 9. toggle_voice_playback
            functionDeclarations.put(JSONObject().apply {
                put("name", "toggle_voice_playback")
                put("description", "Terminal natijalarini ovozli o'qish (TTS) rejimini yoqadi yoki o'chiradi.")
            })

            // 10. share_output
            functionDeclarations.put(JSONObject().apply {
                put("name", "share_output")
                put("description", "Terminaldagi joriy natijalarni boshqa ilovalarga ulashish oynasini ochadi.")
            })

            // 11. press_virtual_key
            functionDeclarations.put(JSONObject().apply {
                put("name", "press_virtual_key")
                put("description", "Pastki paneldagi virtual boshqaruv klavishasini bosadi.")
                put("parameters", JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", JSONObject().apply {
                        put("key", JSONObject().apply {
                            put("type", "STRING")
                            put("description", "Bosiladigan klavisha nomi: ESC, TAB, CTRL, UP, DOWN, LEFT, RIGHT, ENTER.")
                        })
                    })
                    put("required", JSONArray().apply { put("key") })
                })
            })

            // 12. toggle_bottom_bar
            functionDeclarations.put(JSONObject().apply {
                put("name", "toggle_bottom_bar")
                put("description", "Pastki navigatsiya menyu panelini (Terminal, Ilovalar, Monitor, Fayllar) yashiradi yoki ko'rsatadi.")
            })

            // 13. switch_view
            functionDeclarations.put(JSONObject().apply {
                put("name", "switch_view")
                put("description", "Ilovaning asosiy oynasini o'zgartiradi (0 = Terminal, 1 = Ilovalar, 2 = Monitor, 3 = Fayllar).")
                put("parameters", JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", JSONObject().apply {
                        put("view", JSONObject().apply {
                            put("type", "INTEGER")
                            put("description", "Oyna indeksi: 0 = Terminal, 1 = Ilovalar (Apps), 2 = Monitor, 3 = Fayllar (Files).")
                        })
                    })
                    put("required", JSONArray().apply { put("view") })
                })
            })

            // 14. generate_image
            functionDeclarations.put(JSONObject().apply {
                put("name", "generate_image")
                put("description", "Foydalanuvchi rasm, surat, illyustratsiya yoki tasvir yaratishni so'raganda chaqiriladi.")
                put("parameters", JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", JSONObject().apply {
                        put("prompt", JSONObject().apply {
                            put("type", "STRING")
                            put("description", "Tasvirning ingliz tilidagi batafsil, badiiy va fotorealistik tavsifi (masalan: 'ultra realistic 8k photo of a sunset over snowy mountains, cinematic lighting').")
                        })
                        put("title", JSONObject().apply {
                            put("type", "STRING")
                            put("description", "Rasmning foydalanuvchi tilidagi qisqa va chiroyli nomi.")
                        })
                    })
                    put("required", JSONArray().apply { put("prompt"); put("title") })
                })
            })

            // 15. generate_audio
            functionDeclarations.put(JSONObject().apply {
                put("name", "generate_audio")
                put("description", "Foydalanuvchi musiqa, audio, kuy, fon musiqasi yoki ohang yaratishni so'raganda chaqiriladi.")
                put("parameters", JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", JSONObject().apply {
                        put("title", JSONObject().apply {
                            put("type", "STRING")
                            put("description", "Audioning nomi (masalan: 'Sokin oqshom kuyi').")
                        })
                        put("type", JSONObject().apply {
                            put("type", "STRING")
                            put("description", "Audio turi: 'melody' (fortepiano/melodiya), 'ambient' (sokin fon), 'nature' (yomg'ir va tabiat), 'retro' (8-bit arcade), 'beats' (lo-fi ritm).")
                        })
                        put("prompt", JSONObject().apply {
                            put("type", "STRING")
                            put("description", "Audioning qisqa tavsifi.")
                        })
                    })
                    put("required", JSONArray().apply { put("title"); put("type") })
                })
            })

            // 16. generate_video
            functionDeclarations.put(JSONObject().apply {
                put("name", "generate_video")
                put("description", "Foydalanuvchi video, animatsiya yoki klip yaratishni so'raganda chaqiriladi.")
                put("parameters", JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", JSONObject().apply {
                        put("title", JSONObject().apply {
                            put("type", "STRING")
                            put("description", "Videoning nomi.")
                        })
                        put("prompt", JSONObject().apply {
                            put("type", "STRING")
                            put("description", "Video sahnasining inglizcha tavsifi (masalan: 'matrix digital code rain cyber neon').")
                        })
                        put("style", JSONObject().apply {
                            put("type", "STRING")
                            put("description", "Video uslubi: 'matrix', 'space', 'nature', 'cyberpunk', 'abstract'.")
                        })
                    })
                    put("required", JSONArray().apply { put("title"); put("prompt") })
                })
            })

            // 17. create_file
            functionDeclarations.put(JSONObject().apply {
                put("name", "create_file")
                put("description", "Foydalanuvchi biror dasturlash yoki matnli fayl (Python, Shell skript, HTML, JSON, matn, CSV va hk.) yaratishni so'raganda chaqiriladi. Fayl telefon xotirasida haqiqiy fayl sifatida yaratiladi.")
                put("parameters", JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", JSONObject().apply {
                        put("filename", JSONObject().apply {
                            put("type", "STRING")
                            put("description", "Fayl nomi va kengaytmasi (masalan: 'script.py', 'bot.py', 'index.html', 'data.json', 'run.sh', 'report.txt').")
                        })
                        put("content", JSONObject().apply {
                            put("type", "STRING")
                            put("description", "Faylning to'liq, mukammal va ishlaydigan kodi yoki matni.")
                        })
                        put("description", JSONObject().apply {
                            put("type", "STRING")
                            put("description", "Fayl haqida qisqa tushuntirish.")
                        })
                    })
                    put("required", JSONArray().apply { put("filename"); put("content") })
                })
            })

            // 18. search_web
            functionDeclarations.put(JSONObject().apply {
                put("name", "search_web")
                put("description", "Internetdan (Google/Web) so'nggi ma'lumotlar, yangiliklar, faktlar, narxlar va jonli ma'lumotlarni qidiradi.")
                put("parameters", JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", JSONObject().apply {
                        put("query", JSONObject().apply {
                            put("type", "STRING")
                            put("description", "Internetdan qidiriladigan so'rov yoki kalit so'zlar (masalan: 'bugungi ob havo Toshkent', 'so'nggi texnologiya yangiliklari').")
                        })
                    })
                    put("required", JSONArray().apply { put("query") })
                })
            })

            toolObj.put("functionDeclarations", functionDeclarations)
            toolsArray.put(toolObj)

            root.put("tools", toolsArray)

            // Generation config
            val configObj = JSONObject()
            configObj.put("temperature", 0.2) // Low temperature for high precision function calling
            root.put("generationConfig", configObj)

            val jsonMedia = "application/json; charset=utf-8".toMediaType()
            val requestBody = root.toString().toRequestBody(jsonMedia)

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val responseBodyStr = response.body?.string() ?: ""
                    val code = response.code
                    if (code == 429 || code == 503 || code == 504 || code == 500) {
                        Log.w(TAG, "API transient error (will fallback): Code $code, Body: $responseBodyStr")
                    } else {
                        Log.e(TAG, "API call failed: Code $code, Body: $responseBodyStr")
                    }
                    val detailedMsg = try {
                        val json = JSONObject(responseBodyStr)
                        val errorObj = json.optJSONObject("error")
                        errorObj?.optString("message", "") ?: ""
                    } catch (e: Exception) {
                        ""
                    }
                    val errorText = if (code == 429) {
                        when (currentLanguage) {
                            "uz" -> "Gemini API so'rovlar limitiga yetdi (HTTP 429). Iltimos, 5-10 sekund kutib qayta urinib ko'ring yoki boshqa AI modelini tanlang."
                            "ru" -> "Превышен лимит запросов Gemini API (HTTP 429). Пожалуйста, подождите 5-10 секунд и попробуйте снова."
                            else -> "Gemini API rate limit reached (HTTP 429). Please wait 5-10 seconds and try again."
                        }
                    } else {
                        "Gemini API xatoligi (HTTP ${code})" + if (detailedMsg.isNotEmpty()) " - $detailedMsg" else ""
                    }
                    return@withContext GeminiResponse(
                        text = errorText,
                        toolCalls = emptyList(),
                        shouldFallback = (code == 429 || code == 503 || code == 504 || code == 500)
                    )
                }

                val reader = response.body?.byteStream()?.bufferedReader() ?: return@withContext GeminiResponse(text = "Bo'sh javob", toolCalls = emptyList())
                val textResponseBuilder = StringBuilder(1024)
                val toolCallsList = mutableListOf<ToolCall>()
                var lastUiUpdateTime = 0L
                var hasPendingUiUpdate = false
                val throttleIntervalMs = 50L // 30-60 ms throttle window for smooth, stutter-free Compose rendering

                try {
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.startsWith("data: ")) {
                            val dataJsonStr = line.substring(6).trim()
                            if (dataJsonStr == "[DONE]") continue
                            if (dataJsonStr.isEmpty()) continue
                            
                            try {
                                val chunkJson = JSONObject(dataJsonStr)
                                val candidates = chunkJson.optJSONArray("candidates")
                                if (candidates != null && candidates.length() > 0) {
                                    val firstCandidate = candidates.getJSONObject(0)
                                    val contentObj = firstCandidate.optJSONObject("content")
                                    if (contentObj != null) {
                                        val parts = contentObj.optJSONArray("parts")
                                        if (parts != null) {
                                            for (i in 0 until parts.length()) {
                                                val part = parts.getJSONObject(i)
                                                val text = part.optString("text", "")
                                                if (text.isNotEmpty()) {
                                                    textResponseBuilder.append(text)
                                                    hasPendingUiUpdate = true
                                                    val now = SystemClock.uptimeMillis()
                                                    if (now - lastUiUpdateTime >= throttleIntervalMs) {
                                                        lastUiUpdateTime = now
                                                        hasPendingUiUpdate = false
                                                        val currentText = textResponseBuilder.toString()
                                                        withContext(Dispatchers.Main) {
                                                            onUpdate(currentText)
                                                        }
                                                    }
                                                }
                                                
                                                val functionCall = part.optJSONObject("functionCall")
                                                if (functionCall != null) {
                                                    val name = functionCall.getString("name")
                                                    val argsObj = functionCall.optJSONObject("args")
                                                    val argsMap = mutableMapOf<String, Any>()
                                                    if (argsObj != null) {
                                                        val keys = argsObj.keys()
                                                        while (keys.hasNext()) {
                                                            val key = keys.next()
                                                            argsMap[key] = argsObj.get(key)
                                                        }
                                                    }
                                                    toolCallsList.add(ToolCall(name, argsMap))
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Chunk parse error", e)
                            }
                        }
                    }

                    // Flush any pending text chunk that arrived within the final throttle window
                    if (hasPendingUiUpdate) {
                        val finalText = textResponseBuilder.toString()
                        withContext(Dispatchers.Main) {
                            onUpdate(finalText)
                        }
                        hasPendingUiUpdate = false
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: java.io.IOException) {
                    if (e.message?.contains("closed") != true && e.message?.contains("Canceled") != true) {
                        Log.e(TAG, "Stream read error", e)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Stream error", e)
                }

                val finalText = textResponseBuilder.toString()
                GeminiResponse(
                    text = finalText,
                    toolCalls = toolCallsList,
                    actualModelId = modelName
                )
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "generateContent error", e)
            GeminiResponse(
                text = "Xatolik yuz berdi: ${e.localizedMessage}",
                toolCalls = emptyList()
            )
        }
    }
}
