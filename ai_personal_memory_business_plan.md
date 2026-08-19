# Business Plan — AI Personal Memory Capture Platform

## 1. Executive Summary

The business will develop an **AI-powered personal memory platform** that continuously captures spoken conversations and events throughout a user's day, automatically transcribes them, and converts them into useful, searchable memory.

The initial hardware platform will be a **new, inexpensive Android phone combined with a discreet external microphone**. The phone is not intended to function primarily as the customer's everyday smartphone. It becomes a dedicated capture appliance: reliable battery, storage, Android, Wi-Fi/Bluetooth, and commodity hardware at low cost.

The Android-phone approach provides a fast route to market without designing custom electronics. At the same time, the architecture will remain hardware-independent so that a purpose-built pendant or OEM wearable recorder can be added later without rebuilding the AI platform.

The core proposition is not “another voice recorder app.” It is:

> **An AI that remembers your day.**

The customer buys a complete, configured system rather than having to discover, install, configure, and integrate several applications themselves.

---

## 2. The Problem

People routinely forget:

- what was said in conversations;
- names and details about people they meet;
- commitments they made;
- tasks they agreed to perform;
- useful ideas that arose spontaneously;
- instructions and information received verbally;
- where and when an important discussion occurred.

AI transcription and summarisation can solve much of this, but existing dedicated AI recorders often create dependence on proprietary hardware and recurring subscriptions.

Ordinary voice-recorder apps solve only the capture problem. They generally do not deliver the complete experience of continuous capture, automatic transfer, transcription, memory extraction, organisation, search, and follow-up.

The opportunity is therefore to combine **commodity capture hardware with purpose-built software and AI processing**.

---

## 3. Product Concept

### Version 1 — Android Capture Device

The first commercial product consists of:

1. A **new Android smartphone** selected from a certified list of suitable models.
2. A small wired or Bluetooth microphone, preferably worn near the collar.
3. Our preinstalled Android capture application.
4. Automatic transfer of recordings to the AI processing system.
5. A web/mobile interface for reviewing and searching the resulting personal memory.

The Android phone can remain in a trouser or jacket pocket while a small microphone provides better and more consistent audio.

A wired microphone may be particularly attractive because it eliminates:

- charging another battery;
- Bluetooth pairing problems;
- wireless dropouts;
- receiver dongles;
- significant additional power consumption.

The user's primary phone remains completely available for calls, messaging, navigation, and normal use.

---

## 4. Proof of Concept

An initial real-world experiment has already been conducted using a **Samsung Galaxy A54 running BlackBox**.

In a quiet room, the A54 successfully recorded intelligible speech while located inside a trouser pocket.

This establishes an important first feasibility point: a normal Android phone can act as a wearable continuous audio capture device.

Further testing should cover:

- walking;
- clothing movement;
- outdoor environments;
- vehicles;
- noisy environments;
- conversations at 1–3 metres;
- 8–12+ hour recording sessions;
- battery consumption;
- thermal behaviour;
- screen-off/background reliability;
- wired microphones;
- Bluetooth microphones.

---

## 5. Why Not Simply Sell an App?

A standalone Android recording app is unlikely to be sufficiently differentiated.

BlackBox demonstrates that continuous background recording itself is achievable, but the commercial proposition should not be “our version of BlackBox.”

The physical product changes the proposition.

Customers receive a **ready-to-use AI memory appliance** rather than being expected to:

- find a spare phone;
- select recording software;
- configure Android battery settings;
- choose a microphone;
- manage recordings;
- arrange uploads;
- select transcription software;
- configure AI processing.

The value is in delivering the entire working system.

A software-only “bring your own Android” version may nevertheless be offered as a lower-cost entry product and customer acquisition channel.

---

## 6. Hardware Strategy

### Do Not Optimise for the Absolute Cheapest Phone

An additional A$30–50 in hardware cost is relatively insignificant if it substantially increases:

- reliability;
- battery life;
- storage;
- Android compatibility;
- microphone compatibility;
- product lifetime;
- customer satisfaction.

The goal should therefore be the **cheapest sufficiently reliable platform**, not the cheapest Android phone available.

### Initial Target Specification

A sensible target is:

| Component | Target |
|---|---|
| Android | Android 14+ preferred |
| RAM | 4 GB preferred |
| Storage | 64 GB minimum; 128 GB preferred |
| Battery | 5,000 mAh preferred |
| Wi-Fi | Required |
| Bluetooth | Required |
| External microphone | 3.5 mm TRRS and/or USB-C |
| Cellular | Optional/not important |
| 5G | Not required |
| Camera quality | Not important |
| Screen quality | Not important |
| NFC | Not required |

The device does not necessarily require a SIM. Wi-Fi can handle upload and synchronisation.

---

## 7. Australian Sourcing Strategy

Directly importing generic smartphones from China is not the preferred initial strategy.

A low factory quotation does not represent the true commercial cost. Potential additional costs and risks include:

- international freight;
- GST;
- customs/brokerage;
- Australian regulatory/compliance requirements;
- chargers and electrical compliance;
- warranty administration;
- defective units;
- replacement inventory;
- changing internal components between production runs;
- disappearing models;
- firmware inconsistencies;
- minimum order quantities;
- capital tied up in inventory.

Instead, the initial business should favour **new phones supplied through Australian retail, prepaid, wholesale, or distribution channels**.

### Certified Device List

The software should not depend on one handset.

Instead, maintain a certified hardware list such as:

- selected Motorola models;
- selected TCL models;
- selected Optus/ZTE models;
- selected Telstra models;
- selected Samsung models where economically appropriate.

When one model becomes unavailable or expensive, another certified device can replace it.

This substantially reduces hardware-obsolescence risk.

---

## 8. Prepaid Phones as an Opportunity

Australian prepaid smartphones may offer particularly attractive economics.

Carriers and retailers periodically discount phones substantially because they expect revenue from telecommunications service.

Our dedicated capture device may not require cellular service at all.

Therefore, carrier locking may be irrelevant when the device operates primarily over Wi-Fi, subject to the conditions of each particular offer and commercial resale considerations.

This creates the possibility of acquiring hardware whose underlying capability substantially exceeds its purchase price.

However, the business plan should **not rely on temporary promotional pricing**. Promotions should improve margins rather than determine whether the business works.

---

## 9. Current Hardware Candidates

The Motorola Moto G35 5G demonstrates the level of hardware obtainable cheaply during promotions:

- 4 GB RAM;
- 128 GB storage;
- 5,000 mAh battery;
- Android;
- Wi-Fi/Bluetooth;
- 3.5 mm headset/microphone connection;
- USB-C.

Promotional Australian pricing has reached approximately A$99, although normal pricing can be substantially higher.

The Optus X Plus represents a lower-specification alternative:

- 2 GB RAM;
- 64 GB storage;
- 5,000 mAh battery;
- Android Go;
- 3.5 mm connection.

It may be adequate for pure capture but offers less performance headroom.

The preferred commercial sourcing range is provisionally **A$90–150 per new phone**, provided the model meets reliability requirements.

---


## 9A. Camera-Free Smart Glasses — Third Capture Hardware Path

A further hardware variation is **camera-free smart audio glasses**. These can potentially provide a more natural wearable form factor than either a phone microphone or pendant while avoiding much of the privacy concern associated with camera-equipped smart glasses.

The glasses strategy should be treated as a third capture path alongside the dedicated Android device and future pendant.

### Option A — Glasses as the External Microphone

The simplest implementation is:

**camera-free audio glasses → Bluetooth → dedicated Android capture phone → our Android application → Audio Ingestion API**

In this configuration, the glasses do not need their own recording storage, SDK, Wi-Fi, or AI processing. They function primarily as a well-positioned wearable microphone, while the Android device remains in the user's pocket and handles continuous recording, buffering, storage, networking, and upload.

Potential advantages include:

- microphone position close to the user's mouth;
- no microphone cable running under clothing;
- normal everyday wearable appearance;
- open ears rather than earbuds;
- no visible camera;
- retention of the inexpensive Android platform and existing capture application;
- potentially better and more consistent audio than a phone microphone inside a pocket.

The key technical questions are whether each glasses model exposes its microphone as a standard Android Bluetooth input that our application can continuously capture, and how long its battery lasts under **continuous microphone transmission**, rather than ordinary standby or music playback.

Current camera-free audio glasses demonstrate that this is already a real commodity category. Products range from inexpensive generic Bluetooth glasses to established-brand audio frames. Some inexpensive examples advertise only around 4–5 hours of call use, while better audio glasses advertise substantially longer general/listening battery life. Therefore battery testing under our exact continuous-capture workload is essential.

### Option B — Glasses as the Complete Capture Device

A more advanced version is:

**camera-free smart glasses → onboard microphone/storage → periodic Bluetooth/Wi-Fi transfer → Audio Ingestion API**

This would reduce or potentially eliminate the dedicated Android capture phone, but requires substantially more capable hardware.

Suitable glasses would need some combination of:

- onboard storage;
- programmable firmware or Android/Linux platform;
- accessible SDK/API;
- reliable audio recording;
- sufficient battery life;
- Bluetooth and/or Wi-Fi transfer;
- manufacturer support.

This path is technically closer to the pendant/OEM project and should remain a research track rather than a dependency for Version 1.

### Commercial Positioning

Camera-free glasses may become a particularly attractive premium product because they look like an ordinary item people already wear.

The lack of a camera should be an intentional design choice. It reduces the immediate social concern created by camera-equipped smart glasses and keeps the product focused on **personal audio memory rather than visual surveillance**.

Potential product tiers therefore become:

1. **Bring Your Own Android** — software for a compatible existing phone.
2. **Dedicated Memory Device** — new certified Android phone plus discreet wired/Bluetooth microphone.
3. **Memory Glasses** — camera-free audio glasses paired with the Android/backend system.
4. **Future Memory Pendant** — purpose-built OEM wearable feeding the same backend.

All tiers should feed the same Audio Ingestion API and AI memory engine.

### Glasses Qualification Tests

Candidate glasses should be evaluated for:

- absence of camera;
- microphone intelligibility for the wearer;
- capture of other speakers at conversational distance;
- continuous microphone battery life;
- Android Bluetooth microphone routing;
- compatibility with our foreground recording service;
- connection stability;
- comfort over 8+ hours;
- weight;
- prescription-lens compatibility;
- discreet appearance;
- charging time;
- ability to use the glasses while the primary phone remains otherwise functional;
- wholesale and replacement availability.

The glasses path reinforces the central architectural principle: **capture hardware must remain interchangeable.**


## 10. Android Capture Application

The company should develop its own minimal Android recording application rather than remain dependent on BlackBox.

The capture application does not need to reproduce all of BlackBox's user-facing features.

Its primary responsibility is **reliable acquisition and delivery of audio**.

### Core Functions

Version 0.1:

- user starts recording;
- continuous microphone capture;
- operation with screen locked;
- persistent foreground recording service;
- compressed AAC/M4A audio;
- automatic file segmentation, initially around 15–30 minutes;
- timestamped filenames;
- crash-safe file handling.

Version 0.2:

- automatic upload of completed chunks;
- Wi-Fi-only upload option;
- upload retry;
- offline queue;
- verification that uploads completed successfully;
- automatic local deletion according to retention rules;
- device health/status reporting.

Version 0.3:

- microphone selection;
- wired/Bluetooth microphone handling;
- audio-level monitoring;
- detection of microphone disconnection;
- battery/charging status;
- remote configuration;
- encryption.

Modern Android explicitly supports microphone capture through a microphone foreground service, although current Android versions impose permission, user-initiation, foreground-service declaration, and visibility requirements. These requirements should be treated as core engineering constraints rather than attempting to make recording invisible to the device owner.

---

## 11. Continuous Recording vs Voice Activation

The initial product should probably **record continuously rather than rely on sound activation to decide what to capture**.

Voice activation can miss the beginning of speech.

Storage is inexpensive enough that continuous compressed recording is practical.

At approximately 32 kbps mono:

- 8 hours ≈ 115 MB;
- 12 hours ≈ 173 MB;
- 16 hours ≈ 230 MB.

Therefore even a 64–128 GB phone provides considerable local buffering.

Voice Activity Detection should instead be performed **after capture**.

Pipeline:

**Continuous recording**

↓

**Audio chunks**

↓

**Voice Activity Detection**

↓

**Speech regions**

↓

**Transcription**

This preserves context while allowing silence to be ignored during expensive downstream processing.

---

## 12. Server-Side Architecture

The capture device should remain deliberately simple.

### Capture Layer

Android phone + internal microphone

OR

Android phone + wired microphone

OR

Android phone + Bluetooth microphone

OR

future pendant

↓

### Audio Ingestion API

Receives:

- device ID;
- timestamps;
- audio;
- metadata;
- upload integrity information.

↓

### Audio Processing

- Voice Activity Detection;
- noise handling;
- speech segmentation;
- transcription;
- timestamps;
- speaker diarisation;
- potentially speaker recognition with appropriate consent.

↓

### AI Memory Engine

Extract:

- conversations;
- people;
- names;
- facts;
- commitments;
- promises;
- tasks;
- appointments;
- instructions;
- ideas;
- decisions;
- questions requiring follow-up.

↓

### Memory Database

Information becomes searchable by:

- person;
- date;
- conversation;
- subject;
- location where legally/technically appropriate;
- task;
- keyword;
- semantic meaning.

---

## 13. Example User Experience

The user wears the microphone and puts the capture phone in a pocket.

No interaction is required during normal use after recording has been deliberately started.

Later the system could show:

### Today

**10:15 AM — Conversation with Robert**

Discussed radio advertising costs.

**Commitment:** Send revised budget tomorrow.

**Task:** Obtain airtime pricing.

---

**1:42 PM — Conversation**

Discussed inexpensive Android phones as dedicated AI memory devices.

**Idea:** Maintain multiple certified Android hardware models rather than depending on one model.

---

The user could subsequently ask:

> What did Robert ask me to do?

> Who did I promise to contact this week?

> What ideas did I have about the Android recorder project?

> Find the conversation where we discussed microphone costs.

This searchable memory is the real product.

---

## 14. Pendant Strategy — Keep Pending

The pendant/OEM hardware investigation should **not be abandoned**.

Instead it becomes a parallel hardware-development track.

The backend should be intentionally hardware-independent.

Today:

**Android → Audio Ingestion API**

Later:

**Pendant → Audio Ingestion API**

Everything downstream remains unchanged.

Potential future capture hardware includes:

- BLE pendant;
- Wi-Fi pendant;
- standalone recorder with automatic synchronisation;
- OEM module with SDK;
- custom wearable;
- glasses/headset integrations.

This means Android provides a rapid path to market without locking the company permanently into smartphone hardware.

---

## 15. Product Versions

### Complete Device

Customer receives:

- new certified Android device;
- discreet microphone;
- charger/cable;
- preinstalled capture software;
- configured account;
- AI memory service.

This should be the primary commercial offering.

### Bring Your Own Android

Customer installs the application on a compatible existing Android device.

Advantages:

- almost zero hardware cost;
- easy trial;
- larger addressable market;
- customer acquisition funnel;
- useful for technically confident users.

A customer who proves the value using an old phone may later purchase the dedicated appliance.

### Memory Glasses

Camera-free smart audio glasses can act either as the Bluetooth microphone for the Android capture device or, in a later implementation, as a more independent capture platform.

This provides a premium wearable option with potentially excellent microphone positioning and less social concern than camera-equipped smart glasses.

### Future Pendant

Premium/minimal wearable capture device using the same backend.

---

## 16. Indicative Economics

An illustrative initial hardware BOM might be:

| Item | Indicative Cost |
|---|---:|
| New Android phone | A$100–150 |
| Microphone | A$10–30 |
| Cable/accessories | A$5–10 |
| Packaging | A$5–15 |
| Provisioning/testing | A$10–20 |
| **Indicative hardware/provisioning cost** | **A$130–225** |

These figures require validation with real sourcing.

A complete system could potentially sell in approximately the **A$299–399 range**, depending on functionality, positioning, support, and included service.

The business should not attempt to eliminate all subscriptions merely for marketing purposes. Cloud transcription, AI inference, storage, support, and software development create genuine ongoing costs.

The distinction should be:

> **No artificial hardware-history lock-in. Customers pay for services that genuinely incur ongoing costs.**

Potential pricing structures include:

- hardware purchase + modest monthly AI subscription;
- included monthly processing allowance;
- pay-as-you-go transcription/AI;
- local-processing tier;
- premium cloud-memory tier.

---

## 17. Competitive Position

The product competes indirectly with:

- Fieldy-type wearable memory devices;
- Plaud-style AI recorders;
- AI meeting recorders;
- voice recorder applications;
- transcription applications;
- AI note-taking applications.

The differentiation is the combination of:

1. inexpensive commodity hardware;
2. customer ownership of the capture device;
3. continuous all-day operation;
4. dedicated wearable microphone;
5. automatic processing;
6. searchable long-term memory;
7. hardware independence;
8. future pendant compatibility;
9. lower dependence on proprietary hardware ecosystems.

The company is therefore **not fundamentally a recorder company**.

It is an **AI memory platform company**.

---

## 18. Privacy, Consent and Trust

Continuous audio recording creates significant privacy, consent, workplace, and legal considerations.

Privacy must therefore be a core product feature rather than an afterthought.

Product requirements should include:

- clear recording state;
- deliberate user initiation;
- encryption;
- secure authentication;
- configurable local retention;
- deletion controls;
- data export;
- transparent cloud-processing policies;
- clear consent guidance;
- jurisdiction-specific legal review before commercial launch.

The product should never be positioned as a covert-surveillance device.

The objective is personal memory augmentation with appropriate transparency and consent.

---

## 19. Development Roadmap

### Phase 1 — Validate Capture

Use the existing Samsung A54 and BlackBox.

Test:

- pocket recording;
- all-day battery;
- screen-off reliability;
- environmental noise;
- distant speakers;
- microphone positioning.

### Phase 2 — Microphone Testing

Purchase several inexpensive wired and Bluetooth microphones.

Compare:

- intelligibility;
- clothing noise;
- other-speaker capture;
- comfort;
- visibility;
- reliability;
- battery impact.

### Phase 3 — Build Android Capture MVP

Develop:

- microphone foreground service;
- AAC/M4A recording;
- 15–30 minute chunks;
- timestamping;
- crash recovery;
- upload queue.

### Phase 4 — Cheap Phone Qualification

Purchase at least one new A$90–150 Android.

Run continuously for approximately one week.

Measure:

- missed audio;
- battery;
- heat;
- crashes;
- Android process killing;
- microphone behaviour;
- reboot recovery;
- storage consumption.

### Phase 5 — Backend MVP

Implement:

audio upload → VAD → transcription → diarisation → AI extraction → searchable storage.

### Phase 6 — Pilot

Give configured devices to a small number of real users.

Measure whether they actually retrieve useful information from their captured days.

The key metric is not recording hours.

It is:

> **How often did the system help the user remember something valuable that they otherwise would have forgotten?**

### Phase 7 — Commercial Hardware

Establish several certified Android models and Australian supply relationships.

### Parallel Phase — Wearable Hardware Development

Continue evaluating OEM pendant/module manufacturers with SDK/API access.

In parallel, qualify camera-free audio glasses, beginning with their use as Bluetooth microphones paired to the Android capture device. Test real continuous-microphone battery life and Android audio-routing compatibility rather than relying on advertised music/standby figures.

Integrate suitable pendant or glasses hardware into the existing ingestion API when available.

---

## 20. Major Risks

### Android Background Restrictions

Android imposes increasingly strict requirements around microphone foreground services. The product must remain compliant with current Android foreground-service and permission models.

Mitigation: dedicated capture architecture, persistent visible recording notification, extensive device qualification, and avoiding dependence on unsupported background behaviour.

### Hardware Obsolescence

Cheap phones change frequently.

Mitigation: certify multiple devices and keep hardware-specific code minimal.

### Audio Quality

Pocket microphones may struggle with clothing noise and distant speakers.

Mitigation: external collar microphone and real-world testing.

### Privacy and Legal Risk

Continuous recording can capture people who did not expect to be recorded.

Mitigation: privacy-by-design, clear recording indication, user education, legal review and consent-oriented product design.

### AI Processing Cost

Continuous audio can generate substantial transcription and inference cost at scale.

Mitigation: server-side VAD, efficient transcription, batching, model selection, local processing where economical, and usage-based service tiers.

### User Behaviour

Users may forget to start the recorder, charge the device, or wear the microphone.

Mitigation: extremely simple UX, battery/status alerts and later hardware improvements.

---

## 21. Strategic Principle

The company should avoid prematurely becoming a hardware manufacturer.

Commodity Android hardware lets the business validate the difficult and valuable parts first:

- reliable capture;
- automated processing;
- useful memory extraction;
- retrieval;
- customer willingness to pay.

Only after those are demonstrated should significant capital be invested in custom hardware.

The Android device is therefore not a compromise.

It is a **rapid, replaceable Version 1 hardware platform**.

---

## 22. Immediate Next Actions

1. Continue all-day BlackBox testing on the Samsung A54.
2. Measure battery and storage consumption.
3. Test real conversations in noisy environments.
4. Buy/test several tiny external microphones.
5. Define the Android capture MVP.
6. Build continuous chunk recording on the A54.
7. Add automatic upload.
8. Identify several new Australian Android phones in the A$90–150 range.
9. Purchase one lower-cost phone for qualification.
10. Build the audio-ingestion API.
11. Prototype VAD + transcription + AI memory extraction.
12. Continue pendant/OEM research in parallel.
13. Test camera-free smart audio glasses as an alternative external microphone/wearable interface.
14. Measure continuous Bluetooth microphone battery life and Android routing reliability on candidate glasses.
15. Validate privacy/legal requirements before external pilots.
16. Recruit a small pilot group and measure actual memory-retrieval value.

---

## 23. Vision

The long-term product should not be tied to a particular recorder, phone, pendant, microphone, or AI model.

The durable asset is the **personal memory layer**.

Different devices continuously feed real-world experiences into that layer.

The system turns those experiences into structured, searchable, actionable memory.

The initial Android phone is simply the fastest practical way to begin building that future.
