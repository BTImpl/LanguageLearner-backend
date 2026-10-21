# BE-12 results (model: gemini-flash-lite-latest, resolved to gemini-3.5-flash-lite)

| file | status | questions_found | skipped_invalid | seconds | model_version | prompt_tokens | output_tokens | expected_questions | wrong_text | wrong_options | usable_without_fix | notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1.jpeg | ok | 9 | 0 | 16.8 | gemini-3.5-flash-lite | 1316 | 1231 | 9 | 0 | 0 | yes | Title "34. gyakorlat" is correct. The exercise instruction was not captured (no field for it). The fragment of the previous exercise at the top of the page was correctly ignored. |
| 2.jpeg | ok | 12 | 0 | 19.8 | gemini-3.5-flash-lite | 1316 | 1624 | 12 | 0 | 0 | yes | Title is the running page header, not an exercise title. Printed quirks and punctuation were copied exactly (go., I....). |
| 3 1 (1).jpeg | ok | 11 | 0 | 10.4 | gemini-3.5-flash-lite | 1316 | 1548 | 11 | 0 | 0 | yes | Title is the same running page header. Two-line questions (28, 30, 32) are all correct. Show-through text from the back of the page was ignored. |
| Képernyőfotó 2026-09-04 - 10.38.21.png | ok | 10 | 0 | 10.9 | gemini-3.5-flash-lite | 1316 | 1418 | 10 | 0 | 0 | yes | Screenshot of an answered quiz: the highlighted answer inside the sentence was replaced by "[...]" (other images use "___"). The correct answer was not marked. |
| Képernyőfotó 2026-09-04 - 10.45.40.png | ok | 10 | 0 | 18.4 | gemini-3.5-flash-lite | 1356 | 1356 | 10 | 0 | 0 | yes | Title "Ex. 8: The Passive" is correct. The instruction line was not captured. Question 9 correctly has 3 options. Italics lost (harmless). |
| Képernyőfotó 2026-09-04 - 10.50.28.png | ok | 10 | 0 | 20.6 | gemini-3.5-flash-lite | 1333 | 1335 | 10 | 0 | 0 | yes | No title (correct). Highlighted correct answers were not marked. |
