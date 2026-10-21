# BE-12: photo of a worksheet -> quiz JSON (experiment)

Throwaway experiment that decided whether a small multimodal model can extract multiple-choice
questions from photos of printed worksheets well enough to build the photo-quiz feature (BE-13/BE-14).
It is **not** part of the backend and is not built or deployed.

## Run it

```sh
export GEMINI_API_KEY=...             # free Google AI Studio key, never commit it
cd experiments/be-12
python3 run.py --list-models          # pick a Flash-Lite model
python3 run.py --model gemini-flash-lite-latest
```

Put the images (jpg, png, webp, heic) in `photos/`. Results are written to `results/`
(`summary.csv` and `summary.md` are committed; the per-photo JSON files and the photos are not, because they
contain third-party worksheet text). Python 3 standard library only.

## Result (6 images, 2026-10-21)

- Model: `gemini-flash-lite-latest`, which resolved to `gemini-3.5-flash-lite`. Free AI Studio key, single pass.
- 62 of 62 questions extracted with correct question text and options; nothing needed a manual fix.
- About 16 s per image, about 1.3k input and 1.4k output tokens per image.
- Samples: three photos of a printed grammar book and three screenshots of an online quiz that had already been answered.
  The model did not mark or invent correct answers, as the prompt requires.

## Follow-ups for BE-13

1. The title was sometimes the running page header ("ANGOL NYELVTANI GYAKORLATOK") instead of the exercise heading:
   tell the prompt to return only an exercise title and to ignore running headers.
2. The exercise instruction ("Choose the most appropriate answer.") is lost: consider an optional `instructions` field.
3. Blank marker was inconsistent (`...`, `___`, `[...]`): ask for `___` consistently.
4. Not covered yet: poor lighting, handwritten marks, two-column pages, tables, pages with pictures.
5. Use a pinned model name, not the `-latest` alias, in the backend.

Free-tier note: the Gemini API terms require the paid service for apps made available to users in the EEA,
so this script is only for local experiments; production use needs a billing-enabled project (see BE-16).
