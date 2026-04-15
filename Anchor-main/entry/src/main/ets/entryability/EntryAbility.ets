import { AbilityConstant, ConfigurationConstant, UIAbility, Want } from '@kit.AbilityKit';
import { hilog } from '@kit.PerformanceAnalysisKit';
import { window } from '@kit.ArkUI';
import abilityAccessCtrl, { Permissions } from '@ohos.abilityAccessCtrl';
import { notificationManager } from '@kit.NotificationKit';

const DOMAIN = 0x0000;

export default class EntryAbility extends UIAbility {
  onCreate(want: Want, launchParam: AbilityConstant.LaunchParam): void {
    try {
      this.context.getApplicationContext().setColorMode(ConfigurationConstant.ColorMode.COLOR_MODE_NOT_SET);
    } catch (err) {
      hilog.error(DOMAIN, 'testTag', 'Failed to set colorMode. Cause: %{public}s', JSON.stringify(err));
    }
    hilog.info(DOMAIN, 'testTag', '%{public}s', 'Ability onCreate');
  }

  onDestroy(): void {
    hilog.info(DOMAIN, 'testTag', '%{public}s', 'Ability onDestroy');
  }

  onWindowStageCreate(windowStage: window.WindowStage): void {
    // Main window is created, set main page for this ability
    hilog.info(DOMAIN, 'testTag', '%{public}s', 'Ability onWindowStageCreate');

    windowStage.loadContent('pages/Index', (err) => {
      if (err.code) {
        hilog.error(DOMAIN, 'testTag', 'Failed to load the content. Cause: %{public}s', JSON.stringify(err));
        return;
      }
      hilog.info(DOMAIN, 'testTag', 'Succeeded in loading the content.');

      // Request location permissions after the UI is ready so the system
      // dialog has a window context to attach to.
      this.requestLocationPermissions();

      // Request notification enable immediately after load.
      // If notifications are disabled the caregiver will not receive ANCHOR alerts.
      // Non-fatal if the user dismisses — the app still runs, alerts just won't appear.
      this.requestNotificationEnable();
    });
  }

  // Requests precise + coarse location and call permissions at runtime.
  // HarmonyOS requires both location permissions to be declared and requested together.
  // PLACE_CALL is required for CommunityCard's tap-to-call feature.
  private requestLocationPermissions(): void {
    const permissions: Permissions[] = [
      'ohos.permission.LOCATION',
      'ohos.permission.APPROXIMATELY_LOCATION',
      'ohos.permission.PLACE_CALL'
    ];

    const atManager = abilityAccessCtrl.createAtManager();
    atManager.requestPermissionsFromUser(this.context, permissions)
      .then((result) => {
        // result.authResults: 0 = granted, -1 = denied
        const allGranted = result.authResults.every((r) => r === 0);
        if (allGranted) {
          hilog.info(DOMAIN, 'testTag', 'Location permissions granted');
        } else {
          hilog.warn(DOMAIN, 'testTag', 'Location permissions not fully granted: %{public}s',
            JSON.stringify(result.authResults));
        }
      })
      .catch((err: Error) => {
        hilog.error(DOMAIN, 'testTag', 'Permission request failed: %{public}s', JSON.stringify(err));
      });
  }

  // Checks whether notifications are enabled and shows the system prompt if not.
  // Called once on launch so the caregiver is never silently blocked from alerts.
  private requestNotificationEnable(): void {
    notificationManager.isNotificationEnabled()
      .then((enabled: boolean) => {
        if (!enabled) {
          return notificationManager.requestEnableNotification(this.context);
        }
        hilog.info(DOMAIN, 'testTag', 'Notifications already enabled');
        return Promise.resolve();
      })
      .then(() => {
        hilog.info(DOMAIN, 'testTag', 'Notification enable check complete');
      })
      .catch((err: Error) => {
        // Non-fatal — user may have dismissed the dialog or notification API unavailable.
        hilog.warn(DOMAIN, 'testTag', 'Notification enable request failed: %{public}s', JSON.stringify(err));
      });
  }

  onWindowStageDestroy(): void {
    // Main window is destroyed, release UI related resources
    hilog.info(DOMAIN, 'testTag', '%{public}s', 'Ability onWindowStageDestroy');
  }

  onForeground(): void {
    // Ability has brought to foreground
    hilog.info(DOMAIN, 'testTag', '%{public}s', 'Ability onForeground');
  }

  onBackground(): void {
    // Ability has back to background
    hilog.info(DOMAIN, 'testTag', '%{public}s', 'Ability onBackground');
  }
}