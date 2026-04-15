// NotificationService.ets
// Centralises all caregiver notification publishing for ANCHOR.
// Keeping this separate from GeofenceService means the state machine has no
// knowledge of notification APIs — easier to swap channels later (e.g. local → push kit).
//
// EMULATOR NOTE:
//   Local watch notifications work on the DevEco emulator.
//   Cross-device delivery to the companion phone requires real device pairing
//   and CANNOT be tested on the emulator. The full notification payload is
//   logged to hilog so you can verify the content during emulator testing.
//   Flag this for the hackathon demo: "phone alert requires real device pairing".

import { notificationManager } from '@kit.NotificationKit';
import notification from '@ohos.notification';
import { hilog } from '@kit.PerformanceAnalysisKit';
import common from '@ohos.app.ability.common';

const DOMAIN = 0x0000;

export class NotificationService {

  // ---------------------------------------------------------------------------
  // Drift / geofence alert (ID 1001)
  // ---------------------------------------------------------------------------

  // Sends the ALERT state caregiver notification.
  // Uses a fixed ID so repeated ALERT entries update the existing notification
  // rather than stacking — keeps the caregiver's notification shade clean.
  static sendDriftAlert(patientName: string, distanceM: number): void {
    const distanceText = `${Math.round(distanceM)}m from home`;
    const bodyText = `${patientName} may have left home. Last known: ${distanceText}.`;

    // Log the full payload — verifiable on the emulator even if phone delivery fails.
    hilog.warn(DOMAIN, 'NotificationService',
      '[DRIFT ALERT] Title: ANCHOR Alert | Body: %{public}s', bodyText);

    // ArkTS requires every object literal to match an explicit type (arkts-no-untyped-obj-literals).
    const normalContent: notificationManager.NotificationBasicContent = {
      title: 'ANCHOR Alert',
      text: bodyText,
      additionalText: 'Tap to open ANCHOR'
    };
    const content: notificationManager.NotificationContent = {
      contentType: notification.ContentType.NOTIFICATION_CONTENT_BASIC_TEXT,
      normal: normalContent
    };
    const request: notificationManager.NotificationRequest = {
      id: 1001,
      content: content,
      deliveryTime: Date.now(),
      showDeliveryTime: true
    };

    notificationManager.publish(request)
      .then(() => hilog.info(DOMAIN, 'NotificationService', 'Drift alert published to watch'))
      .catch((err: Error) =>
        hilog.error(DOMAIN, 'NotificationService',
          'Drift alert publish failed: %{public}s', JSON.stringify(err)));
  }

  // ---------------------------------------------------------------------------
  // Fall alert (ID 1002)
  // ---------------------------------------------------------------------------

  // Sends the fall detection caregiver notification.
  // Uses ID 1002 so fall alerts never overwrite in-flight drift alerts (ID 1001).
  // Both can coexist in the notification shade simultaneously.
  static sendFallAlert(patientName: string, locationText: string, timestampMs: number): void {
    const bodyText =
      `${patientName} may have fallen. ` +
      `Location: ${locationText}. Time: ${NotificationService.formatTimestamp(timestampMs)}.`;

    // Log full payload for emulator verification.
    hilog.warn(DOMAIN, 'NotificationService',
      '[FALL ALERT] Title: ANCHOR Fall Alert | Body: %{public}s', bodyText);

    const fallNormal: notificationManager.NotificationBasicContent = {
      title: 'ANCHOR Fall Alert',
      text: bodyText,
      additionalText: 'Tap to open ANCHOR'
    };
    const fallContent: notificationManager.NotificationContent = {
      contentType: notification.ContentType.NOTIFICATION_CONTENT_BASIC_TEXT,
      normal: fallNormal
    };
    const fallRequest: notificationManager.NotificationRequest = {
      id: 1002,
      content: fallContent,
      deliveryTime: timestampMs,
      showDeliveryTime: true
    };

    notificationManager.publish(fallRequest)
      .then(() => hilog.info(DOMAIN, 'NotificationService', 'Fall alert published to watch'))
      .catch((err: Error) =>
        hilog.error(DOMAIN, 'NotificationService',
          'Fall alert publish failed: %{public}s', JSON.stringify(err)));
  }

  // ---------------------------------------------------------------------------
  // Permission / enable helpers
  // ---------------------------------------------------------------------------

  // Returns true if notifications are currently allowed for this app.
  // A false result means the user has disabled notifications — alerts will be silent.
  static async isEnabled(): Promise<boolean> {
    try {
      return await notificationManager.isNotificationEnabled();
    } catch (err) {
      hilog.warn(DOMAIN, 'NotificationService',
        'isNotificationEnabled check failed: %{public}s', JSON.stringify(err));
      // Default to true so we still attempt to publish — the publish call will
      // fail gracefully if permissions are actually denied.
      return true;
    }
  }

  // Shows the system dialog asking the user to enable notifications, if not already enabled.
  // Must be called from EntryAbility with a UIAbilityContext (not a component context).
  // Safe to call on every launch — no-ops if already enabled.
  static async requestEnable(context: common.UIAbilityContext): Promise<void> {
    try {
      const enabled = await notificationManager.isNotificationEnabled();
      if (!enabled) {
        // Shows a HarmonyOS system sheet asking the user to allow notifications.
        await notificationManager.requestEnableNotification(context);
        hilog.info(DOMAIN, 'NotificationService', 'Notification enable dialog shown');
      } else {
        hilog.info(DOMAIN, 'NotificationService', 'Notifications already enabled — no dialog needed');
      }
    } catch (err) {
      // requestEnableNotification can throw if called too early or if the user dismisses.
      // This is non-fatal — the app still works, alerts just won't appear.
      hilog.warn(DOMAIN, 'NotificationService',
        'requestEnable failed (non-fatal): %{public}s', JSON.stringify(err));
    }
  }

  // ---------------------------------------------------------------------------
  // Private helpers
  // ---------------------------------------------------------------------------

  // Formats a Unix timestamp (ms) as HH:MM for the notification body text.
  private static formatTimestamp(timestampMs: number): string {
    const date = new Date(timestampMs);
    const h = date.getHours().toString().padStart(2, '0');
    const m = date.getMinutes().toString().padStart(2, '0');
    return `${h}:${m}`;
  }
}
