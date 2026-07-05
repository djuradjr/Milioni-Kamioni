# Block Brainrot — Privacy Policy

_Last updated: 2026-07-06_

Block Brainrot is a screen-time tracking and app-blocking application. This policy
explains what data the app handles and where it goes.

## The short version

**All data stays on your device.** Block Brainrot has **no internet access** — the
app does not declare the INTERNET permission, so it is technically incapable of
sending any data anywhere. There are no accounts, no analytics, no ads, and no
third-party SDKs that collect data.

## Data the app processes on your device

| Data | Why | Where it is stored |
|---|---|---|
| App usage statistics (which apps you use and for how long) | To show your screen-time dashboard and enforce daily limits | Local database on your device |
| Device unlock / screen-on counts | Shown on your dashboard | Local database on your device |
| Your blocking rules, schedules, focus/sleep settings | To enforce the limits you configure | Local database on your device |
| Optional PIN (stored only as a salted PBKDF2 hash, never in plain text) | To protect your blocking rules from being bypassed | Local app storage on your device |
| Name of the app currently on screen, and (only if you block specific websites) the browser address bar text | Read via Android's Accessibility Service, solely to decide whether to show the block screen | Processed in memory; only aggregate usage time is stored |

## Accessibility Service disclosure

Block Brainrot uses Android's Accessibility Service to detect which app is in the
foreground and, when you have configured website blocking, to read the browser
address bar. This is the only mechanism Android provides to block an app or
website at the moment it is opened. The information read this way is:

- used **only** to enforce the rules you created;
- **never** stored beyond aggregate usage time;
- **never** transmitted off the device (the app cannot access the internet).

## Backups

Backups are disabled (`allowBackup=false`). Your usage history, rules, and PIN
hash are excluded from Google cloud backup and device-to-device transfer.

## Data retention and deletion

Usage history older than 90 days is deleted automatically. You can delete all
data at any time by clearing the app's storage in Android Settings or
uninstalling the app — there is no server-side copy to delete.

## Children

Block Brainrot does not collect personal data from anyone, including children.

## Changes

If this policy changes, the updated version will be published at the same URL
and the "Last updated" date will change.

## Contact

Questions about this policy: djukids028@gmail.com
