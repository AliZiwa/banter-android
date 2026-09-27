# Banter

A group chat where everyone is fictional, the gossip is real, and **nothing leaves the phone**.

You write the cast — a taxi driver who is always five minutes away, an accountant who keeps
receipts — give each one a private secret, and they talk. A language model runs on the device
through [LiteRT-LM](https://github.com/google-ai-edge/LiteRT-LM); each character is a
[Koog](https://github.com/JetBrains/koog) agent wired to it. No server, no API key, no data
bundle after the model is on the phone.

## Running it

Needs Android Studio (JDK 17+) and an `arm64-v8a` or `x86_64` device or emulator, API 26+.

```bash
./gradlew :app:installDebug
./gradlew :app:testDebugUnitTest
```

The app does nothing until a model is installed. Open the chip icon → **Model**.

## Which model

**Use `gemma-4-E2B-it.litertlm` — Google's own conversion.** Ungated, instruction-tuned,
2.59 GB. It is the default URL in the Model screen.

This matters more than anything in this repo. The same code, same cast, same message:

| Model | What came out |
|---|---|
| `gemma-4-E2B-it` (official) | *"Traffic is bad on the way to Mukono right now. Five minutes away."* |
| `Qwen2.5-1.5B-Instruct` | *"I'm good, Nakato. Just a busy auntie with plans and people to handle!"* |
| an "uncensored" Gemma re-upload | *"some seeds sprout in the dust of yesterday"* |

Community "uncensored" re-uploads are measurably worse at following instructions and are not
worth the download. `Gemma3-1B-IT` (gated, ~1 GB) is the fallback for weaker phones.

**Import** works for any `.litertlm` you already have (download on a laptop, move it over).
**Download** takes a URL, plus a Hugging Face token for gated repositories.

To push a model straight onto a debug build, no temp file needed:

```bash
adb shell "run-as com.banter.app sh -c 'cat > /data/data/com.banter.app/files/models/model.litertlm'" < model.litertlm
```

## How it behaves

- **You start the conversation.** Nobody speaks into an empty room.
- **Somebody answers after a beat** — about three seconds, not instantly.
- **If you say nothing for 10–15 seconds, one of the others picks up the last message** and
  carries it on. Every 10–15 seconds, as long as you stay quiet.
- **Each character sees the last two messages.** That is the whole context, on purpose.
- **Typing a message cuts in.** Whatever was being written is dropped; the next reply is to you.
- **It only runs while the chat is on screen.** Generation is the most expensive thing the
  phone does here.

## How it is put together

```
ui/         ChatScreen · CastScreen · ModelScreen · ChatViewModel
agent/      CharacterAgents      one Koog agent per profile
            LiteRtPromptExecutor Koog's seam, backed by the on-device model
director/   Director             the turn loop above
            SpeakerPicker        who talks next
            PromptBuilder        identity + the two lines being replied to
            ReplyCleaner         what small models get wrong on the way out
llm/        EngineManager · LiteRtChatEngine · ModelStore
data/       Scenario · Character · ChatMessage · JSON persistence
```

Each character is a standing Koog `AIAgent` whose system prompt is its profile. Koog usually
talks to hosted providers; `LiteRtPromptExecutor` is where a local runtime plugs in instead.

### Things that are less obvious than they look

**The user must not be called "You".** The transcript reaches the model as `Name: text` lines.
`You: where is Stella?` reads to the model as a statement about *itself*, and the group starts
answering the wrong person. The user gets a real name in prompts (set in the cast screen).

**Don't show a small model a sample line.** "You sound like this: X" makes a 1–2B model say X,
verbatim, every turn. Describe the voice; do not demonstrate it.

**A reply that opens with somebody else's name is not a reply.** The model continuing the
transcript (`Guest: Hello?`) is discarded, not trimmed into a message.

**With two lines of context a character cannot know it has said this before.** The prompt
carries its own last line with "say something new", and an exact repeat is dropped anyway.

**Small models do not stop.** Told to write one line, they write everyone's. Only the first
line survives; labels of any kind are stripped; stage directions and wrapping quotes go.

**Text is only shown when final.** Streaming into a bubble that might then be dropped looks
like a character typing and erasing itself. Generation shows a typing indicator.

## Tests

```bash
./gradlew :app:testDebugUnitTest
```

Concentrated on the messy parts: the turn loop (fake voices on virtual time), reply cleaning,
speaker selection, prompt construction.

## Limits

- The characters are fiction and will say wrong things with total confidence.
- Two messages of context means no long-term memory. That is next week's problem.
- Generation is CPU-bound and warms the phone.
- Quality depends far more on the model than on anything here — see the table above.
