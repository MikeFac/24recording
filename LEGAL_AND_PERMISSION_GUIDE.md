# Pilot Legal, Privacy and Permission Guide

**Draft date:** 20 August 2026  
**Product:** Blackbox Replacement / AI Personal Memory Capture Platform  
**Status:** Internal pilot draft requiring review by a qualified Australian privacy and technology lawyer before external testing, sale, cloud processing, or publication.

This document supplies practical pilot wording and setup instructions. It is not a complete Terms of Service, Privacy Policy, consent process, workplace policy, or legal opinion.

## 1. Recording and consent notice

This application continuously records microphone audio after the user deliberately presses **Start recording**. The persistent Android notification and microphone privacy indicator are intended to make recording visible to the device owner. They do not notify or obtain consent from other people.

The user is responsible for ensuring that every recording, transcription, use, disclosure, and upload is lawful in the place where recording occurs and is permitted by any applicable:

- surveillance and listening-device law;
- privacy and data-protection law;
- workplace, school, venue, court, healthcare, and professional rules;
- confidentiality, non-disclosure, intellectual-property, and contractual obligations; and
- directions given by a person whose voice or information may be captured.

Recording law varies by Australian state and territory and by context. For example, Queensland's current legislation contains a party-to-the-conversation exception to its listening-device prohibition, while other jurisdictions use different tests and may impose separate restrictions on possession, use, communication, or publication. This product must not present a single-jurisdiction exception as permission to record everywhere.

### Conservative operating rule

Before recording, clearly tell affected people that continuous audio recording and possible AI transcription will occur, explain the purpose and intended storage/use, and obtain express consent where required. Stop recording immediately if consent is refused or withdrawn.

Do not record:

- a private conversation in which the user is not an authorised participant;
- where recording is prohibited by law, policy, contract, a court, or the person controlling the premises;
- for covert surveillance, stalking, harassment, discrimination, intimidation, or another unlawful purpose;
- legal, medical, counselling, financial, employment, child-related, or similarly sensitive interactions without specific authority and appropriate safeguards; or
- account credentials, payment details, security codes, or information the user has no legitimate need to collect.

## 2. Pilot acknowledgement shown in the app

Before the first recording, the app asks the user to acknowledge that:

1. recording is continuous after Start is pressed;
2. the user is responsible for legality and consent;
3. recording is not permitted for covert surveillance or unlawful monitoring;
4. audio can contain sensitive information about multiple people;
5. the pilot may miss or inaccurately capture information and is not suitable for safety-critical reliance; and
6. the notice is general information rather than legal advice.

Acknowledgement confirms that the notice was displayed. It does not itself obtain consent from recorded people and must not be represented as doing so.

## 3. Draft product disclaimer

The pilot is supplied for evaluation and personal-memory testing. To the maximum extent permitted by applicable law:

- recording, file segmentation, storage, upload, cleanup, transcription, diarisation, extraction, and search may fail, omit material, duplicate material, or produce inaccurate results;
- the product is not an emergency, safety, medical, legal, evidentiary, compliance, or official recordkeeping system;
- users must independently verify important audio, transcripts, names, dates, commitments, and decisions;
- no representation is made that using the product is lawful in a particular jurisdiction, workplace, venue, or situation;
- availability of an Android permission or technical ability to record does not establish legal authority or consent; and
- users must maintain any independent records required by law, contract, professional duty, safety practice, or organisational policy.

This draft does not exclude, restrict, or modify any guarantee, right, or remedy that cannot lawfully be excluded under the Australian Consumer Law or another applicable law. Final warranty, limitation-of-liability, refund, and consumer-guarantee wording requires legal review and must match the actual commercial offer.

## 4. Draft privacy collection notice

The following placeholders must be completed before data leaves a pilot device:

- **Responsible entity:** `[legal company name and ABN]`
- **Contact:** `[privacy contact email and postal address]`
- **Privacy policy URL:** `[public URL]`
- **Hosting and processing regions:** `[countries/regions]`
- **Cloud subprocessors and AI providers:** `[provider list]`
- **Retention periods:** `[original audio, derivatives, transcripts, logs, backups]`
- **Complaint and access process:** `[procedure and response channel]`

### Current v0.1 behavior

The current implementation stores completed audio files and chunk metadata in app-private storage on the Android device. It does not yet upload, clean, transcribe, or disclose audio to a cloud service. This notice must be updated and shown before any of those capabilities are enabled.

### Notice required before cloud processing

Before enabling upload or AI processing, tell users in clear language:

- what audio, transcript, identity, device, diagnostic, and inferred information is collected;
- why each category is reasonably necessary;
- whether voices may reveal or include sensitive information;
- where originals and derivatives are stored and processed;
- which service providers receive data and whether overseas disclosure occurs;
- whether data is used to train or improve any model;
- retention, backup, deletion, export, access, and correction behavior;
- how security incidents and privacy complaints are handled; and
- how a recorded person, not only the account holder, may make an inquiry or request where applicable.

The OAIC states that an APP privacy policy should explain the kinds of personal information collected, how it is collected and held, its purposes, access/correction and complaint processes, and likely overseas disclosures. Privacy and security design must be completed before the backend is treated as production-ready.

## 5. Android permissions used by v0.1

| Permission/capability | Type | Why it is needed | User action |
|---|---|---|---|
| Microphone (`RECORD_AUDIO`) | Runtime, while-in-use prerequisite | Captures microphone PCM for the recording foreground service | Choose **Allow** when prompted |
| Notifications (`POST_NOTIFICATIONS`) | Runtime on Android 13+ | Shows the persistent capture notification and Stop action in the notification drawer | Choose **Allow** when prompted |
| Foreground service | Manifest | Allows the visible long-running capture service | No separate Android prompt |
| Microphone foreground-service type | Manifest | Declares that the foreground service uses the microphone | No separate Android prompt |
| Wake lock | Manifest | Keeps the CPU available while the screen is off and capture is active | No separate Android prompt |

The app does not currently request contacts, location, camera, phone, SMS, accessibility, storage/media-library, or Bluetooth permissions.

## 6. Setup instructions

### First recording

1. Open **Blackbox Replacement** while the app is visible.
2. Select **Legal and permission guide** and read the pilot notice.
3. Press **Start recording**.
4. Read the first-run notice and select **I understand** only if the operating conditions are acceptable.
5. Choose **Allow** for microphone access.
6. On Android 13 or later, choose **Allow** for notifications.
7. If the app reports missing permissions, open **Legal and permission guide → Open app settings → Permissions** and enable Microphone and Notifications.
8. Press **Start recording** again if Android returned to the app without starting automatically.

### Background reliability

1. Open **Legal and permission guide → Open app settings**.
2. Open **Battery** or **App battery usage**.
3. Select **Unrestricted** or **Allow background usage** where the device provides that option.
4. Do not select **Restricted**. Android documents that Restricted apps may lose foreground services and may not work as expected.
5. Check manufacturer-specific settings such as sleeping apps, auto-launch, background activity, or battery manager and exempt this dedicated capture app where appropriate.
6. Keep the persistent notification enabled. Do not swipe away system indicators or force-stop the app during recording.

### Daily pre-flight check

1. Charge the phone and confirm sufficient free storage.
2. Connect the intended microphone.
3. Make a short test recording and stop it.
4. Confirm the completed and queued chunk counters increased without an error.
5. Start the real session while the Activity is visible.
6. Confirm `RECORDING`, the microphone privacy indicator, and persistent notification.

### Correct shutdown

Use **Stop recording** in the app or notification. Wait until the UI shows `STOPPED`; this allows the current AAC/M4A container to receive its end-of-stream marker, sync to storage, validate, and enter the durable queue.

## 7. Troubleshooting

### Start is unavailable or returns an error

- Confirm Microphone permission is allowed.
- Start from the visible app. Android 14+ checks microphone permission and while-in-use eligibility when a microphone foreground service is created.
- If permission was permanently denied, use App settings rather than waiting for another prompt.

### Notification is missing

- On Android 13+, enable Notifications for the app and the **Audio capture** channel.
- Android can technically launch a foreground service without notification permission, but denied notifications may only be represented in the system task manager. This pilot intentionally requires notification permission to preserve a clear recording state.

### Recording stops with the screen locked

- Ensure app battery usage is not Restricted.
- Disable manufacturer-specific automatic sleeping for this app.
- Confirm the app was not force-stopped and the microphone remained connected.
- Capture device model, Android version, battery mode, and error text for qualification testing.

### Microphone changes or disconnects

Stop the session, reconnect the microphone, and make a test recording. Wired/Bluetooth input selection and disconnect recovery are not yet certified in v0.1.

## 8. Required work before an external pilot

- Obtain jurisdiction-specific legal review covering every proposed pilot location and participant context.
- Complete company identity and contact placeholders.
- Publish an accurate APP privacy policy and collection notice.
- Define consent evidence and withdrawal procedures.
- Add in-product deletion, export, retention, and incident-response processes.
- Execute data-processing agreements with cloud and AI providers.
- Confirm data residency, overseas disclosure, model-training, and subprocessor terms.
- Add a versioned re-consent flow when material processing or policy terms change.
- Review accessibility, child-safety, workplace-monitoring, consumer-law, and professional-context requirements.

## 9. Authoritative references

- [Android microphone foreground-service requirements](https://developer.android.com/about/versions/14/changes/fgs-types-required)
- [Android restrictions on starting foreground services from the background](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)
- [Android notification runtime permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission)
- [Android background and battery optimization](https://developer.android.com/topic/performance/background-optimization)
- [OAIC Australian Privacy Principles guidelines](https://www.oaic.gov.au/privacy/australian-privacy-principles/australian-privacy-principles-guidelines)
- [OAIC APP 1: open and transparent management](https://www.oaic.gov.au/privacy/australian-privacy-principles/australian-privacy-principles-guidelines/chapter-1-app-1-open-and-transparent-management-of-personal-information)
- [Queensland Invasion of Privacy Act 1971, section 43](https://www.legislation.qld.gov.au/link?doc.id=act-1971-050&id=sec.43&type=act)
- [NSW Surveillance Devices Act 2007](https://legislation.nsw.gov.au/view/html/inforce/current/act-2007-64)
