import { DriftState } from '../common/DriftState';
import { GeofenceService } from '../services/GeofenceService';
import { AppConfig } from '../config/AppConfig';
import { MotionDetector } from '../services/MotionDetector';
import { MockMotionDetector } from '../services/MockMotionDetector';
import { RealMotionDetector } from '../services/RealMotionDetector';
import { FallDetector, MockFallDetector, RealFallDetector } from '../services/FallDetector';
import common from '@ohos.app.ability.common';
import geoLocationManager from '@ohos.geoLocationManager';
import { hilog } from '@kit.PerformanceAnalysisKit';
import { router } from '@kit.ArkUI';

const DOMAIN = 0x0000;

enum AppPage {
  MENU = 'MENU',
  HOME = 'HOME',
  SET_FENCE = 'SET_FENCE',
  CHECK_LOCATION = 'CHECK_LOCATION',
  DEBUG = 'DEBUG',
  DEBUG_WALKING = 'DEBUG_WALKING',
  DEBUG_STILL = 'DEBUG_STILL',
  DEBUG_FALL = 'DEBUG_FALL'
}

const HOME_CANVAS = 130;
const FENCE_CANVAS = 110;
const RING_RATIO = 0.80; // alert ring (50m) fills this fraction of canvas half-width

@Entry
@Component
struct Index {

  // --- Navigation ---
  @State private page: AppPage = AppPage.MENU;

  // --- Geofence state ---
  // NOT_SET is the correct initial value — SAFE must never show without a confirmed anchor.
  @State private driftState: DriftState = DriftState.NOT_SET;
  @State private distanceMetres: number = 0;
  @State private bearing: number = 0;              // radians, clockwise from north
  @State private hasAnchor: boolean = false;
  @State private hasReceivedFix: boolean = false;  // true once a real GPS update arrives
  @State private isFallDetected: boolean = false;

  // Epoch ms of the last successful GPS fix — shown as "Updated HH:MM:SS" in the UI.
  @State private lastUpdateTime: number = 0;

  // GPS accuracy (metres) from the last fix on the Set Fence screen.
  // -1 means no fix has been obtained yet on this screen session.
  @State private gpsAccuracy: number = -1;

  // --- Check Location page ---
  @State private currentLat: number = 0;
  @State private currentLng: number = 0;
  @State private isFetchingLocation: boolean = false;
  @State private locationError: string = '';

  // --- Set Fence page ---
  @State private isSettingAnchor: boolean = false;

  // --- Canvas ---
  private renderCfg: RenderingContextSettings = new RenderingContextSettings(true);
  private homeCtx: CanvasRenderingContext2D = new CanvasRenderingContext2D(this.renderCfg);
  private fenceCtx: CanvasRenderingContext2D = new CanvasRenderingContext2D(this.renderCfg);
  private homeCanvasReady: boolean = false;
  private fenceCanvasReady: boolean = false;

  private geofenceService: GeofenceService | null = null;

  // Concrete mock detector references — needed by the debug panel to call
  // simulateWalking() / simulateStill() / simulateFall() which are not on the interface.
  // null when IS_EMULATOR is false (real device uses real detectors without these methods).
  private mockMotion: MockMotionDetector | null = null;
  private mockFall: MockFallDetector | null = null;

  // --- Debug pages ---
  // Live lat/lng shown on the DEBUG_WALKING page, refreshed every 3s.
  @State private debugLat: number = 0;
  @State private debugLng: number = 0;
  // setInterval handle — cleared when navigating away from the walking debug page.
  private debugCoordTimer: number = -1;

  // ------------------------------------------------------------------
  // Lifecycle
  // ------------------------------------------------------------------

  aboutToAppear(): void {
    const context = getContext(this) as common.UIAbilityContext;

    // Choose the right detectors based on build target.
    // Emulator: mock detectors (no sensor hardware). Real watch: real detectors.
    let motionDet: MotionDetector;
    let fallDet: FallDetector;

    if (AppConfig.IS_EMULATOR) {
      const mockM = new MockMotionDetector();
      const mockF = new MockFallDetector();
      this.mockMotion = mockM;
      this.mockFall = mockF;
      motionDet = mockM;
      fallDet = mockF;
    } else {
      motionDet = new RealMotionDetector();
      fallDet = new RealFallDetector();
    }

    this.geofenceService = new GeofenceService(
      context,
      (state: DriftState, distance: number, brng: number) => {
        this.driftState = state;
        this.distanceMetres = distance;
        this.bearing = brng;
        this.hasReceivedFix = true;
        // Record the time of every state update so the "Updated at" label stays current.
        this.lastUpdateTime = Date.now();
        this.redrawCanvases();
      },
      () => { this.isFallDetected = true; },
      motionDet,
      fallDet
    );

    this.geofenceService.init().then(() => {
      this.hasAnchor = this.geofenceService!.hasAnchor();
      this.geofenceService!.startMonitoring();
    });
  }

  aboutToDisappear(): void {
    this.geofenceService?.stopMonitoring();
  }

  // ------------------------------------------------------------------
  // GPS helpers — shared by Set Fence and Check Location
  // ------------------------------------------------------------------

  // Checks that location is enabled before making any location call.
  // Returns false and sets locationError if not.
  private locationEnabled(): boolean {
    // Emulator workaround: isLocationEnabled() always returns false on the DevEco
    // emulator even when coordinates are injected. Bypass the check in emulator mode.
    const gpsOn = AppConfig.IS_EMULATOR || geoLocationManager.isLocationEnabled();
    if (!gpsOn) {
      this.locationError = 'Location is off. Enable it in watch Settings.';
      return false;
    }
    this.locationError = '';
    return true;
  }

  // Shared request config — 10 s timeout prevents hanging indefinitely.
  private makeLocationRequest(): geoLocationManager.CurrentLocationRequest {
    const req: geoLocationManager.CurrentLocationRequest = {
      priority: geoLocationManager.LocationRequestPriority.FIRST_FIX,
      scenario: geoLocationManager.LocationRequestScenario.UNSET,
      maxAccuracy: 0,
      timeoutMs: 10000
    };
    return req;
  }

  // Returns a Promise<Location> using getLastLocation() on the emulator
  // (reads injected coordinates immediately) or getCurrentLocation() on a real device.
  // The emulator does not respond to getCurrentLocation() one-shot requests — it times out.
  // getLastLocation() is synchronous — wrap it in a Promise so callers stay uniform.
  private getLocation(): Promise<geoLocationManager.Location> {
    if (AppConfig.IS_EMULATOR) {
      // Try getLastLocation() first — returns injected coordinates if the emulator
      // toolbar has been used. If it throws (no cached fix yet), fall back to the
      // hardcoded Singapore demo coordinates so the demo always works.
      try {
        const loc: geoLocationManager.Location = geoLocationManager.getLastLocation();
        return Promise.resolve(loc);
      } catch (_err) {
        const fallback: geoLocationManager.Location = {
          latitude: 1.35210,
          longitude: 103.81980,
          altitude: 0,
          accuracy: 10,
          altitudeAccuracy: 0,
          speed: 0,
          timeStamp: Date.now(),
          timeSinceBoot: 0,
          direction: 0,
          additions: [],
          additionSize: 0
        };
        hilog.warn(DOMAIN, 'Index', 'getLastLocation failed — using hardcoded emulator fallback');
        return Promise.resolve(fallback);
      }
    }
    return geoLocationManager.getCurrentLocation(this.makeLocationRequest());
  }

  private setHomePoint(): void {
    if (!this.locationEnabled()) return;
    this.isSettingAnchor = true;
    this.gpsAccuracy = -1; // reset accuracy until new fix arrives

    this.getLocation()
      .then((location: geoLocationManager.Location) => {
        // Store GPS accuracy so the UI can show "±Xm" to the caregiver.
        this.gpsAccuracy = location.accuracy;

        this.geofenceService?.setAnchorPoint(location.latitude, location.longitude)
          .then(() => {
            this.isSettingAnchor = false;
            this.hasAnchor = true;
            // Snap distance to 0 — the new anchor IS the current position.
            this.distanceMetres = 0;
            this.bearing = 0;
            this.lastUpdateTime = Date.now();
            this.redrawCanvases();
            hilog.info(DOMAIN, 'Index', 'Anchor set at %{public}f, %{public}f',
              location.latitude, location.longitude);
          });
      })
      .catch((err: Error) => {
        this.isSettingAnchor = false;
        this.locationError = 'Could not get a GPS fix. Inject coordinates in the emulator toolbar first.';
        hilog.error(DOMAIN, 'Index', 'setHomePoint failed: %{public}s', JSON.stringify(err));
      });
  }

  private fetchCurrentLocation(): void {
    if (!this.locationEnabled()) return;
    this.isFetchingLocation = true;
    this.locationError = '';

    this.getLocation()
      .then((location: geoLocationManager.Location) => {
        this.currentLat = location.latitude;
        this.currentLng = location.longitude;
        this.isFetchingLocation = false;
        hilog.info(DOMAIN, 'Index', 'Location fix: %{public}f, %{public}f',
          location.latitude, location.longitude);
      })
      .catch((err: Error) => {
        this.isFetchingLocation = false;
        this.locationError = 'Could not get a GPS fix. Inject coordinates in the emulator toolbar first.';
        hilog.error(DOMAIN, 'Index', 'fetchLocation failed: %{public}s', JSON.stringify(err));
      });
  }

  // ------------------------------------------------------------------
  // Canvas drawing
  // ------------------------------------------------------------------

  private redrawCanvases(): void {
    if (this.homeCanvasReady) {
      this.drawMap(this.homeCtx, HOME_CANVAS);
    }
    if (this.fenceCanvasReady) {
      this.drawMap(this.fenceCtx, FENCE_CANVAS);
    }
  }

  // Draws the geofence radar:
  //   green fill = safe zone (30 m)
  //   amber ring = 30 m drifting boundary
  //   red dashed ring = 50 m alert boundary
  //   white dot = home / anchor (always at centre)
  //   coloured dot = user at real bearing + scaled distance from GPS
  private drawMap(ctx: CanvasRenderingContext2D, size: number): void {
    const cx = size / 2;
    const cy = size / 2;
    const scale = (cx * RING_RATIO) / 50; // px per metre

    ctx.clearRect(0, 0, size, size);
    ctx.fillStyle = '#141414';
    ctx.fillRect(0, 0, size, size);

    // Safe zone fill
    ctx.fillStyle = '#4ADE8018';
    ctx.beginPath();
    ctx.arc(cx, cy, 30 * scale, 0, Math.PI * 2);
    ctx.fill();

    // 30 m amber ring
    ctx.strokeStyle = '#FBBF2470';
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.arc(cx, cy, 30 * scale, 0, Math.PI * 2);
    ctx.stroke();

    // 50 m red dashed ring
    ctx.strokeStyle = '#F8717170';
    ctx.lineWidth = 1.5;
    ctx.setLineDash([4, 3]);
    ctx.beginPath();
    ctx.arc(cx, cy, 50 * scale, 0, Math.PI * 2);
    ctx.stroke();
    ctx.setLineDash([]);

    // N label
    ctx.fillStyle = '#444444';
    ctx.font = `${Math.round(size * 0.08)}px sans-serif`;
    ctx.textAlign = 'center';
    ctx.fillText('N', cx, size * 0.1);

    // Home dot at centre
    ctx.fillStyle = '#FFFFFF';
    ctx.beginPath();
    ctx.arc(cx, cy, 3, 0, Math.PI * 2);
    ctx.fill();

    // User dot — real bearing from GPS, capped at canvas edge
    // bearing = 0 → north (top), π/2 → east (right), π → south (bottom)
    const pxDist = Math.min(this.distanceMetres * scale, cx - 9);
    const ux = cx + pxDist * Math.sin(this.bearing);
    const uy = cy - pxDist * Math.cos(this.bearing);

    // Halo ring
    ctx.strokeStyle = this.statusColor() + '50';
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.arc(ux, uy, 7, 0, Math.PI * 2);
    ctx.stroke();

    // Filled dot
    ctx.fillStyle = this.statusColor();
    ctx.beginPath();
    ctx.arc(ux, uy, 3.5, 0, Math.PI * 2);
    ctx.fill();
  }

  // ------------------------------------------------------------------
  // Helpers
  // ------------------------------------------------------------------

  // Starts polling getLastLocation() every 3s so the DEBUG_WALKING page shows live coords.
  private startDebugCoordTimer(): void {
    if (this.debugCoordTimer !== -1) {
      clearInterval(this.debugCoordTimer);
    }
    this.debugCoordTimer = setInterval(() => {
      try {
        const loc: geoLocationManager.Location = geoLocationManager.getLastLocation();
        this.debugLat = loc.latitude;
        this.debugLng = loc.longitude;
      } catch (_err) {
        // no cached fix yet — leave the previous values displayed
      }
    }, 3000) as number;
  }

  private stopDebugCoordTimer(): void {
    if (this.debugCoordTimer !== -1) {
      clearInterval(this.debugCoordTimer);
      this.debugCoordTimer = -1;
    }
  }

  // Returns the display label for the current drift state.
  // NOT_SET is the no-anchor state — must never show SAFE without a real anchor.
  private statusLabel(): string {
    if (this.driftState === DriftState.NOT_SET) return 'Setup Required';
    if (this.driftState === DriftState.SAFE) return 'SAFE';
    if (this.driftState === DriftState.DRIFTING) return 'DRIFTING';
    return 'ALERT';
  }

  // Returns the hex colour for the current drift state.
  private statusColor(): string {
    if (this.driftState === DriftState.NOT_SET) return '#FBBF24'; // amber — action required
    if (this.driftState === DriftState.SAFE) return '#4ADE80';
    if (this.driftState === DriftState.DRIFTING) return '#FBBF24';
    return '#F87171';
  }

  private distanceLabel(): string {
    if (!this.hasAnchor) return 'No home set';
    if (!this.hasReceivedFix) return 'Measuring...';
    if (this.distanceMetres < 5) return 'At home';
    return `${Math.round(this.distanceMetres)} m from home`;
  }

  // Formats the last GPS update time as "Updated HH:MM:SS".
  // Returns empty string if no update has been received yet.
  private lastUpdateLabel(): string {
    if (this.lastUpdateTime === 0) return '';
    const date = new Date(this.lastUpdateTime);
    const h = date.getHours().toString().padStart(2, '0');
    const m = date.getMinutes().toString().padStart(2, '0');
    const s = date.getSeconds().toString().padStart(2, '0');
    return `Updated ${h}:${m}:${s}`;
  }

  private formatCoord(value: number, pos: string, neg: string): string {
    return `${Math.abs(value).toFixed(4)}° ${value >= 0 ? pos : neg}`;
  }

  // ------------------------------------------------------------------
  // Root build
  // ------------------------------------------------------------------

  build() {
    Stack({ alignContent: Alignment.Center }) {

      // Page content layer
      if (this.page === AppPage.MENU) {
        this.MenuPage();
      } else if (this.page === AppPage.HOME) {
        this.HomePage();
      } else if (this.page === AppPage.SET_FENCE) {
        this.SetFencePage();
      } else if (this.page === AppPage.DEBUG) {
        this.DebugPage();
      } else if (this.page === AppPage.DEBUG_WALKING) {
        this.DebugWalkingPage();
      } else if (this.page === AppPage.DEBUG_STILL) {
        this.DebugStillPage();
      } else if (this.page === AppPage.DEBUG_FALL) {
        this.DebugFallPage();
      } else {
        this.CheckLocationPage();
      }


      // Fall overlay — highest layer, always blocks everything underneath
      if (this.isFallDetected) {
        this.FallOverlay();
      }

    }
    .width('100%')
    .height('100%')
    .backgroundColor('#0D0D0D')
  }

  // ------------------------------------------------------------------
  // Menu — default view, primary status display
  // ------------------------------------------------------------------
  @Builder
  MenuPage() {
    // space: 4 keeps rows compact enough to fit all four items on a round watch screen.
    Column({ space: 4 }) {

      // App title — white, bold, centred.
      // Long-press 3 s to unlock the hidden debug panel.
      Text('ANCHOR')
        .fontSize(16)
        .fontWeight(FontWeight.Bold)
        .fontColor('#FFFFFF')
        .textAlign(TextAlign.Center)
        .gesture(
          LongPressGesture({ duration: 3000, repeat: false })
            .onAction(() => {
              hilog.info(DOMAIN, 'Index', 'Debug page unlocked via long-press');
              this.page = AppPage.DEBUG;
            })
        )

      // Status badge pill — border and dot colour track the live drift state.
      Row({ space: 5 }) {
        Text('●')
          .fontSize(8)
          .fontColor(this.statusColor())
        Text('STATUS: ' + this.statusLabel())
          .fontSize(10)
          .fontWeight(FontWeight.Bold)
          .fontColor(this.statusColor())
      }
      .padding({ left: 10, right: 10, top: 4, bottom: 4 })
      .borderRadius(12)
      .border({ width: 1, color: this.statusColor() })

      // ── Row 1 — CURRENT STATUS ──────────────────────────────────────
      // Border colour matches drift state for at-a-glance status on the menu.
      // Icon: 👤 person silhouette — represents the monitored individual.
      Row({ space: 10 }) {
        Text('👤')
          .fontSize(16)
          .width(28)
          .height(28)
          .borderRadius(14)
          .backgroundColor('#252525')
          .textAlign(TextAlign.Center)
        Column({ space: 2 }) {
          Text('CURRENT STATUS')
            .fontSize(12)
            .fontWeight(FontWeight.Bold)
            .fontColor('#FFFFFF')
          Text(this.lastUpdateLabel() === '' ? 'Tap to view' : this.lastUpdateLabel())
            .fontSize(10)
            .fontColor('#9CA3AF')
        }
        .alignItems(HorizontalAlign.Start)
        .layoutWeight(1)
      }
      .width('92%')
      .padding({ left: 12, right: 12, top: 5, bottom: 5 })
      .borderRadius(12)
      .backgroundColor('#1A1A1A')
      .border({ width: 1, color: this.statusColor() })
      .onClick(() => { this.page = AppPage.HOME; })

      // ── Row 2 — MANAGE GEOFENCE ─────────────────────────────────────
      // Icon: 📍 location pin — represents the home anchor point.
      Row({ space: 10 }) {
        Text('📍')
          .fontSize(16)
          .width(28)
          .height(28)
          .borderRadius(14)
          .backgroundColor('#252525')
          .textAlign(TextAlign.Center)
        Column({ space: 2 }) {
          Text('MANAGE GEOFENCE')
            .fontSize(12)
            .fontWeight(FontWeight.Bold)
            .fontColor('#FFFFFF')
          Text(this.hasAnchor ? '1 Active Fence' : 'No fence set')
            .fontSize(10)
            .fontColor('#9CA3AF')
        }
        .alignItems(HorizontalAlign.Start)
        .layoutWeight(1)
      }
      .width('92%')
      .padding({ left: 12, right: 12, top: 5, bottom: 5 })
      .borderRadius(12)
      .backgroundColor('#1A1A1A')
      .onClick(() => {
        this.locationError = '';
        this.gpsAccuracy = -1;
        this.page = AppPage.SET_FENCE;
      })

      // ── Row 3 — LIVE TRACKING ───────────────────────────────────────
      // Icon: 🎯 target — represents active GPS tracking.
      Row({ space: 10 }) {
        Text('🎯')
          .fontSize(16)
          .width(28)
          .height(28)
          .borderRadius(14)
          .backgroundColor('#252525')
          .textAlign(TextAlign.Center)
        Column({ space: 2 }) {
          Text('LIVE TRACKING')
            .fontSize(12)
            .fontWeight(FontWeight.Bold)
            .fontColor('#FFFFFF')
          Text(this.distanceLabel())
            .fontSize(10)
            .fontColor('#9CA3AF')
        }
        .alignItems(HorizontalAlign.Start)
        .layoutWeight(1)
      }
      .width('92%')
      .padding({ left: 12, right: 12, top: 5, bottom: 5 })
      .borderRadius(12)
      .backgroundColor('#1A1A1A')
      .onClick(() => {
        this.fetchCurrentLocation();
        this.page = AppPage.CHECK_LOCATION;
      })

      // ── Row 4 — COMMUNITY HUB ───────────────────────────────────────
      // Icon: 👥 two people — represents emergency contacts.
      Row({ space: 10 }) {
        Text('👥')
          .fontSize(16)
          .width(28)
          .height(28)
          .borderRadius(14)
          .backgroundColor('#252525')
          .textAlign(TextAlign.Center)
        Column({ space: 2 }) {
          Text('COMMUNITY CARD')
            .fontSize(12)
            .fontWeight(FontWeight.Bold)
            .fontColor('#FFFFFF')
          Text('Personal Information')
            .fontSize(10)
            .fontColor('#9CA3AF')
        }
        .alignItems(HorizontalAlign.Start)
        .layoutWeight(1)
      }
      .width('92%')
      .padding({ left: 12, right: 12, top: 5, bottom: 5 })
      .borderRadius(12)
      .backgroundColor('#1A1A1A')
      .onClick(() => {
        router.pushUrl({ url: 'pages/CommunityCard' });
      })

      // Simulate Fall removed — access via debug panel (long-press ANCHOR title).

    }
    .width('100%')
    .height('100%')
    .justifyContent(FlexAlign.Center)
    .alignItems(HorizontalAlign.Center)
    .padding({ left: 8, right: 8, top: 5, bottom: 5 })
  }

  // ------------------------------------------------------------------
  // Home (Status) — live status card + radar
  // ------------------------------------------------------------------
  @Builder
  HomePage() {
    Column({ space: 5 }) {

      this.BackButton();

      // Main status label — large, high-contrast, immediately readable
      Text(this.statusLabel())
        .fontSize(22)
        .fontWeight(FontWeight.Bold)
        .fontColor(this.statusColor())

      if (this.driftState === DriftState.NOT_SET) {
        // No anchor configured — guide the caregiver to set one
        Text('Set a home point first')
          .fontSize(11)
          .fontColor('#9CA3AF')
          .textAlign(TextAlign.Center)

        Button('Set Fence')
          .fontSize(12)
          .fontWeight(FontWeight.Medium)
          .fontColor('#0D0D0D')
          .backgroundColor('#9CA3AF')
          .width('68%')
          .height(34)
          .borderRadius(17)
          .onClick(() => {
            this.locationError = '';
            this.gpsAccuracy = -1;
            this.page = AppPage.SET_FENCE;
          })
      } else {
        // Anchor exists — show distance and last update time
        Text(this.distanceLabel())
          .fontSize(12)
          .fontColor('#FFFFFF')

        // "Updated HH:MM:SS" — tells caregiver when the last GPS fix arrived
        if (this.lastUpdateLabel() !== '') {
          Text(this.lastUpdateLabel())
            .fontSize(10)
            .fontColor('#666666')
        }

        Canvas(this.homeCtx)
          .width(HOME_CANVAS)
          .height(HOME_CANVAS)
          .borderRadius(HOME_CANVAS / 2)
          .onReady(() => {
            this.homeCanvasReady = true;
            this.drawMap(this.homeCtx, HOME_CANVAS);
          })

        Row({ space: 10 }) {
          Text('— 30 m')
            .fontSize(9)
            .fontColor('#FBBF24')
          Text('- - 50 m')
            .fontSize(9)
            .fontColor('#F87171')
        }
      }

    }
    .width('100%')
    .height('100%')
    .justifyContent(FlexAlign.Center)
    .alignItems(HorizontalAlign.Center)
    .padding({ left: 14, right: 14, top: 4, bottom: 4 })
  }

  // ------------------------------------------------------------------
  // Set Fence — anchor setup with GPS status check
  // ------------------------------------------------------------------
  @Builder
  SetFencePage() {
    Column({ space: 5 }) {

      this.BackButton();

      Text('Set Fence')
        .fontSize(13)
        .fontWeight(FontWeight.Bold)
        .fontColor('#FFFFFF')

      // GPS enabled status — shown before the button so the caregiver knows
      // whether a fix is likely before they tap.
      if (!AppConfig.IS_EMULATOR && !geoLocationManager.isLocationEnabled()) {
        Text('GPS is off — enable Location in Settings')
          .fontSize(9)
          .fontColor('#F87171')
          .textAlign(TextAlign.Center)
          .width('85%')
      } else if (this.gpsAccuracy > 0) {
        // Show accuracy after a successful fix
        Text(`GPS ready — accuracy ±${Math.round(this.gpsAccuracy)} m`)
          .fontSize(9)
          .fontColor('#4ADE80')
          .textAlign(TextAlign.Center)
          .width('85%')
      } else {
        Text('GPS available — tap to set home point')
          .fontSize(9)
          .fontColor('#9CA3AF')
          .textAlign(TextAlign.Center)
          .width('85%')
      }

      Text(this.hasAnchor ? '● Home point set' : '○ No home point')
        .fontSize(10)
        .fontColor(this.hasAnchor ? '#4ADE80' : '#AAAAAA')

      Canvas(this.fenceCtx)
        .width(80)
        .height(80)
        .borderRadius(40)
        .onReady(() => {
          this.fenceCanvasReady = true;
          this.drawMap(this.fenceCtx, 80);
        })

      // Error message if GPS failed or is off
      if (this.locationError !== '') {
        Text(this.locationError)
          .fontSize(9)
          .fontColor('#F87171')
          .textAlign(TextAlign.Center)
          .width('85%')
      }

      // Disable button if GPS is off or a fix is in progress
      Button(this.isSettingAnchor ? 'Locating...' : (this.hasAnchor ? 'Reset Home Point' : 'Set Home Point'))
        .fontSize(12)
        .fontWeight(FontWeight.Medium)
        .fontColor('#0D0D0D')
        .backgroundColor((AppConfig.IS_EMULATOR || geoLocationManager.isLocationEnabled()) ? '#4ADE80' : '#3A3A3A')
        .width('78%')
        .height(34)
        .borderRadius(17)
        .enabled(!this.isSettingAnchor && (AppConfig.IS_EMULATOR || geoLocationManager.isLocationEnabled()))
        .onClick(() => { this.setHomePoint(); })

    }
    .width('100%')
    .height('100%')
    .justifyContent(FlexAlign.Center)
    .alignItems(HorizontalAlign.Center)
    .padding({ left: 14, right: 14, top: 4, bottom: 4 })
  }

  // ------------------------------------------------------------------
  // Check Location — live GPS + drift state + instructions if no anchor
  // ------------------------------------------------------------------
  @Builder
  CheckLocationPage() {
    Scroll() {
      Column({ space: 8 }) {

        this.BackButton();

        Text('Location')
          .fontSize(14)
          .fontWeight(FontWeight.Bold)
          .fontColor('#FFFFFF')

        // If no anchor: guide the caregiver to set one instead of showing raw GPS
        if (!this.hasAnchor) {
          Text('No home point set')
            .fontSize(13)
            .fontColor('#9CA3AF')
            .fontWeight(FontWeight.Medium)
            .textAlign(TextAlign.Center)

          Text('Go to Set Fence to anchor a home point first.')
            .fontSize(10)
            .fontColor('#666666')
            .textAlign(TextAlign.Center)
            .width('85%')

          Button('Set Fence')
            .fontSize(12)
            .fontWeight(FontWeight.Medium)
            .fontColor('#0D0D0D')
            .backgroundColor('#9CA3AF')
            .width('68%')
            .height(34)
            .borderRadius(17)
            .onClick(() => {
              this.locationError = '';
              this.gpsAccuracy = -1;
              this.page = AppPage.SET_FENCE;
            })
        } else if (this.isFetchingLocation) {
          // Loading state
          Column({ space: 6 }) {
            LoadingProgress()
              .width(28)
              .height(28)
              .color('#4ADE80')
            Text('Finding your location…')
              .fontSize(11)
              .fontColor('#AAAAAA')
              .textAlign(TextAlign.Center)
          }
        } else if (this.locationError !== '') {
          Text(this.locationError)
            .fontSize(11)
            .fontColor('#F87171')
            .textAlign(TextAlign.Center)
            .width('85%')
        } else if (this.currentLat === 0 && this.currentLng === 0) {
          Column({ space: 6 }) {
            LoadingProgress()
              .width(28)
              .height(28)
              .color('#4ADE80')
            Text('Finding your location…')
              .fontSize(11)
              .fontColor('#AAAAAA')
              .textAlign(TextAlign.Center)
          }
        } else {
          // Current drift state — colour-coded for instant recognition
          Row({ space: 4 }) {
            Text('●')
              .fontSize(10)
              .fontColor(this.statusColor())
            Text(this.statusLabel())
              .fontSize(12)
              .fontColor(this.statusColor())
              .fontWeight(FontWeight.Bold)
          }

          // Distance readout
          Text(this.distanceLabel())
            .fontSize(12)
            .fontColor('#FFFFFF')
            .fontWeight(FontWeight.Medium)

          // GPS coordinates
          Text(this.formatCoord(this.currentLat, 'N', 'S'))
            .fontSize(11)
            .fontColor('#AAAAAA')
          Text(this.formatCoord(this.currentLng, 'E', 'W'))
            .fontSize(11)
            .fontColor('#AAAAAA')

          // Last update time
          if (this.lastUpdateLabel() !== '') {
            Text(this.lastUpdateLabel())
              .fontSize(10)
              .fontColor('#555555')
          }
        }

        // Refresh button — always shown (except while fetching) when anchor exists
        if (this.hasAnchor) {
          Button(this.isFetchingLocation ? 'Refreshing…' : 'Refresh')
            .fontSize(12)
            .fontColor('#FFFFFF')
            .backgroundColor('#1E1E1E')
            .width('58%')
            .height(34)
            .borderRadius(17)
            .enabled(!this.isFetchingLocation)
            .onClick(() => { this.fetchCurrentLocation(); })
        }

      }
      .width('100%')
      .alignItems(HorizontalAlign.Center)
      .padding({ left: 14, right: 14, top: 6, bottom: 16 })
    }
    .scrollable(ScrollDirection.Vertical)
    .scrollBar(BarState.Off)
    .width('100%')
    .height('100%')
  }

  // ------------------------------------------------------------------
  // Shared back button
  // ------------------------------------------------------------------
  @Builder
  BackButton() {
    Text('< Back')
      .fontSize(11)
      .fontColor('#666666')
      .padding({ top: 3, bottom: 3, left: 6, right: 6 })
      .onClick(() => { this.page = AppPage.MENU; })
  }

  // ------------------------------------------------------------------
  // Fall overlay — full-screen blocking, no tap-through
  // ------------------------------------------------------------------
  @Builder
  FallOverlay() {
    Column({ space: 14 }) {

      Text('FALL DETECTED')
        .fontSize(20)
        .fontWeight(FontWeight.Bold)
        .fontColor('#F87171')
        .textAlign(TextAlign.Center)

      Text('Caregiver alerted.')
        .fontSize(13)
        .fontColor('#FFFFFF')
        .textAlign(TextAlign.Center)

      // Large button — tap to dismiss overlay, long-press to go back to home screen
      Button('Dismiss')
        .fontSize(14)
        .fontWeight(FontWeight.Bold)
        .fontColor('#0D0D0D')
        .backgroundColor('#F87171')
        .width('65%')
        .height(52)
        .borderRadius(26)
        .onClick(() => { this.isFallDetected = false; })
        .gesture(
          LongPressGesture({ duration: 800, repeat: false })
            .onAction(() => {
              this.isFallDetected = false;
              this.page = AppPage.MENU;
            })
        )

      Text('Long-press to go home')
        .fontSize(9)
        .fontColor('#88888888')
        .textAlign(TextAlign.Center)

    }
    .width('100%')
    .height('100%')
    // Solid near-opaque background — blocks the page content visually
    .backgroundColor('#F2000000')
    .justifyContent(FlexAlign.Center)
    .alignItems(HorizontalAlign.Center)
    .padding(20)
    // Block ALL touch events from reaching elements underneath this overlay.
    // This is the fix for the "buttons visible and tappable behind overlay" bug.
    .hitTestBehavior(HitTestMode.Block)
  }

  // ------------------------------------------------------------------
  // DEBUG_WALKING page — confirms walking is active, prints coords every 3s
  // ------------------------------------------------------------------
  @Builder
  DebugWalkingPage() {
    Column({ space: 10 }) {

      Text('< Back')
        .fontSize(11)
        .fontColor('#666666')
        .padding({ top: 3, bottom: 3, left: 6, right: 6 })
        .onClick(() => {
          this.stopDebugCoordTimer();
          this.page = AppPage.MENU;
        })

      Text('Walking Mode Active')
        .fontSize(16)
        .fontWeight(FontWeight.Bold)
        .fontColor('#FBBF24')

      Text('Coordinates update every 3s')
        .fontSize(10)
        .fontColor('#666666')

      Text(this.debugLat !== 0
        ? `${this.debugLat.toFixed(5)}, ${this.debugLng.toFixed(5)}`
        : 'Waiting for GPS fix...')
        .fontSize(13)
        .fontColor('#FFFFFF')
        .textAlign(TextAlign.Center)

      // Live drift state so you can see it change as you inject coords
      Row({ space: 6 }) {
        Text('●').fontSize(10).fontColor(this.statusColor())
        Text(this.statusLabel()).fontSize(12).fontColor(this.statusColor()).fontWeight(FontWeight.Bold)
      }

      Text(`${Math.round(this.distanceMetres)} m from home`)
        .fontSize(11)
        .fontColor('#AAAAAA')

      Text('Inject new coords in emulator\ntoolbar to trigger DRIFTING / ALERT')
        .fontSize(9)
        .fontColor('#555555')
        .textAlign(TextAlign.Center)
        .width('85%')

    }
    .width('100%')
    .height('100%')
    .justifyContent(FlexAlign.Center)
    .alignItems(HorizontalAlign.Center)
    .padding({ left: 16, right: 16, top: 8, bottom: 8 })
  }

  // ------------------------------------------------------------------
  // DEBUG_STILL page — confirms still mode is active
  // ------------------------------------------------------------------
  @Builder
  DebugStillPage() {
    Column({ space: 10 }) {

      Text('< Back')
        .fontSize(11)
        .fontColor('#666666')
        .padding({ top: 3, bottom: 3, left: 6, right: 6 })
        .onClick(() => { this.page = AppPage.MENU; })

      Text('Still Mode Active')
        .fontSize(16)
        .fontWeight(FontWeight.Bold)
        .fontColor('#4ADE80')

      Text('Motion detection stopped.\nState will return to SAFE\nregardless of GPS position.')
        .fontSize(11)
        .fontColor('#AAAAAA')
        .textAlign(TextAlign.Center)

      Row({ space: 6 }) {
        Text('●').fontSize(10).fontColor(this.statusColor())
        Text(this.statusLabel()).fontSize(12).fontColor(this.statusColor()).fontWeight(FontWeight.Bold)
      }

    }
    .width('100%')
    .height('100%')
    .justifyContent(FlexAlign.Center)
    .alignItems(HorizontalAlign.Center)
    .padding({ left: 16, right: 16, top: 8, bottom: 8 })
  }

  // ------------------------------------------------------------------
  // DEBUG_FALL page — confirms fall was triggered (overlay appears on top)
  // ------------------------------------------------------------------
  @Builder
  DebugFallPage() {
    Column({ space: 10 }) {

      Text('< Back')
        .fontSize(11)
        .fontColor('#666666')
        .padding({ top: 3, bottom: 3, left: 6, right: 6 })
        .onClick(() => { this.page = AppPage.MENU; })

      Text('Fall Signal Sent')
        .fontSize(16)
        .fontWeight(FontWeight.Bold)
        .fontColor('#F87171')

      Text('The fall overlay should have\nappeared above this screen.\nDismiss it to return.')
        .fontSize(11)
        .fontColor('#AAAAAA')
        .textAlign(TextAlign.Center)

    }
    .width('100%')
    .height('100%')
    .justifyContent(FlexAlign.Center)
    .alignItems(HorizontalAlign.Center)
    .padding({ left: 16, right: 16, top: 8, bottom: 8 })
  }

  // ------------------------------------------------------------------
  // Debug page — full page, no overlay, so buttons definitely receive touches.
  // Reached by long-pressing the ANCHOR title for 3 seconds.
  // ------------------------------------------------------------------
  @Builder
  DebugPage() {
    Column({ space: 12 }) {

      Text('⚙ Debug')
        .fontSize(14)
        .fontWeight(FontWeight.Bold)
        .fontColor('#FFFFFF')

      Button('Simulate Walking')
        .fontSize(13)
        .fontColor('#0D0D0D')
        .backgroundColor('#FBBF24')
        .width('82%')
        .height(42)
        .borderRadius(21)
        .onClick(() => {
          this.mockMotion?.simulateWalking();
          this.startDebugCoordTimer();
          this.page = AppPage.DEBUG_WALKING;
        })

      Button('Simulate Still')
        .fontSize(13)
        .fontColor('#0D0D0D')
        .backgroundColor('#4ADE80')
        .width('82%')
        .height(42)
        .borderRadius(21)
        .onClick(() => {
          this.mockMotion?.simulateStill();
          this.stopDebugCoordTimer();
          this.page = AppPage.DEBUG_STILL;
        })

      Button('Simulate Fall')
        .fontSize(13)
        .fontColor('#FFFFFF')
        .backgroundColor('#F87171')
        .width('82%')
        .height(42)
        .borderRadius(21)
        .onClick(() => {
          this.page = AppPage.DEBUG_FALL;
          this.mockFall?.simulateFall();
        })

      Button('Close Debug')
        .fontSize(12)
        .fontColor('#AAAAAA')
        .backgroundColor('#1E1E1E')
        .width('82%')
        .height(36)
        .borderRadius(18)
        .onClick(() => {
          this.page = AppPage.MENU;
        })

    }
    .width('100%')
    .height('100%')
    .justifyContent(FlexAlign.Center)
    .alignItems(HorizontalAlign.Center)
    .backgroundColor('#0D0D0D')
    .padding({ left: 16, right: 16, top: 8, bottom: 8 })
  }
}
