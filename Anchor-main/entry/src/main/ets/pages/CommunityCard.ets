import { call } from '@kit.TelephonyKit';
import { BusinessError } from '@kit.BasicServicesKit';
import { promptAction } from '@kit.ArkUI';
import router from '@ohos.router';

interface ContactInfo {
  name: string;
  relationship: string;
  phone: string;
}

interface CommunityCardModel {
  fullName: string;
  homeAddress: string;
  preferredLanguage: string;
  emergencyContact: ContactInfo;
  secondaryContact: ContactInfo;
  bloodType: string;
  allergies: string[];
  medicalSummary: string;
  lastUpdatedAt: string;
}

@Entry
@Component
struct CommunityCard {
  private readonly PROFILE_STORAGE_KEY: string = 'anchorCommunityProfileV1';
  private readonly PAGE_WIDTH: string = '76%';
  private readonly PAGE_HEIGHT: string = '82%';
  private readonly MEDICAL_PAGE_HEIGHT: string = '70%';
  private readonly EDIT_PAGE_WIDTH: string = '84%';

  @StorageLink('anchorCommunityProfileV1')
  storedProfileText: string = '';

  @State editFullName: string = '';
  @State editHomeAddress: string = '';
  @State editPreferredLanguage: string = '';
  @State editEmergencyName: string = '';
  @State editEmergencyRelationship: string = '';
  @State editEmergencyPhone: string = '';
  @State editSecondaryName: string = '';
  @State editSecondaryRelationship: string = '';
  @State editSecondaryPhone: string = '';
  @State editBloodType: string = '';
  @State editAllergies: string = '';
  @State editMedicalSummary: string = '';
  @State editMessage: string = '';
  @State hasInitStorage: boolean = false;
  @State profileRenderVersion: number = 0;

  @State displayFullName: string = '';
  @State displayHomeAddress: string = '';
  @State displayPreferredLanguage: string = '';
  @State displayEmergencyName: string = '';
  @State displayEmergencyRelationship: string = '';
  @State displayEmergencyPhone: string = '';
  @State displaySecondaryName: string = '';
  @State displaySecondaryRelationship: string = '';
  @State displaySecondaryPhone: string = '';
  @State displayBloodType: string = '';
  @State displayAllergiesText: string = '';
  @State displayMedicalSummary: string = '';
  @State displayLastUpdatedAt: string = '';

  @State profile: CommunityCardModel = {
    fullName: 'Tan Mei Lin',
    homeAddress: 'Blk 221 Ang Mo Kio Ave 1, #09-102, Singapore 560221',
    preferredLanguage: 'English, Mandarin',
    emergencyContact: {
      name: 'Daniel Tan',
      relationship: 'Son',
      phone: '+65 9173 4826'
    },
    secondaryContact: {
      name: 'Lim Siew Hoon',
      relationship: 'Neighbor',
      phone: '+65 8264 3951'
    },
    bloodType: 'O+',
    allergies: ['Penicillin', 'Peanuts'],
    medicalSummary: 'Mild dementia. Hypertension medication at 8AM and 8PM.',
    lastUpdatedAt: '2026-03-13 09:30'
  };

  aboutToAppear(): void {
    if (!this.hasInitStorage) {
      PersistentStorage.persistProp(this.PROFILE_STORAGE_KEY, '');
      this.hasInitStorage = true;
    }
    this.loadProfileFromStorage();
    this.syncDisplayFieldsFromProfile();
    this.syncEditFieldsFromProfile();
  }

  private syncDisplayFieldsFromProfile(): void {
    this.displayFullName = this.profile.fullName;
    this.displayHomeAddress = this.profile.homeAddress;
    this.displayPreferredLanguage = this.profile.preferredLanguage;
    this.displayEmergencyName = this.profile.emergencyContact.name;
    this.displayEmergencyRelationship = this.profile.emergencyContact.relationship;
    this.displayEmergencyPhone = this.profile.emergencyContact.phone;
    this.displaySecondaryName = this.profile.secondaryContact.name;
    this.displaySecondaryRelationship = this.profile.secondaryContact.relationship;
    this.displaySecondaryPhone = this.profile.secondaryContact.phone;
    this.displayBloodType = this.profile.bloodType;
    this.displayAllergiesText = this.profile.allergies.join(', ');
    this.displayMedicalSummary = this.profile.medicalSummary;
    this.displayLastUpdatedAt = this.profile.lastUpdatedAt;
  }

  private loadProfileFromStorage(): void {
    if (this.storedProfileText.trim().length === 0) {
      return;
    }
    try {
      const parsed: CommunityCardModel = JSON.parse(this.storedProfileText) as CommunityCardModel;
      this.profile = {
        fullName: parsed.fullName ?? this.profile.fullName,
        homeAddress: parsed.homeAddress ?? this.profile.homeAddress,
        preferredLanguage: parsed.preferredLanguage ?? this.profile.preferredLanguage,
        emergencyContact: {
          name: parsed.emergencyContact?.name ?? this.profile.emergencyContact.name,
          relationship: parsed.emergencyContact?.relationship ?? this.profile.emergencyContact.relationship,
          phone: parsed.emergencyContact?.phone ?? this.profile.emergencyContact.phone
        },
        secondaryContact: {
          name: parsed.secondaryContact?.name ?? this.profile.secondaryContact.name,
          relationship: parsed.secondaryContact?.relationship ?? this.profile.secondaryContact.relationship,
          phone: parsed.secondaryContact?.phone ?? this.profile.secondaryContact.phone
        },
        bloodType: parsed.bloodType ?? this.profile.bloodType,
        allergies: parsed.allergies ?? this.profile.allergies,
        medicalSummary: parsed.medicalSummary ?? this.profile.medicalSummary,
        lastUpdatedAt: parsed.lastUpdatedAt ?? this.profile.lastUpdatedAt
      };
    } catch (_err) {
      this.editMessage = 'Stored profile is invalid. Using current values.';
    }
  }

  private syncEditFieldsFromProfile(): void {
    this.editFullName = this.profile.fullName;
    this.editHomeAddress = this.profile.homeAddress;
    this.editPreferredLanguage = this.profile.preferredLanguage;
    this.editEmergencyName = this.profile.emergencyContact.name;
    this.editEmergencyRelationship = this.profile.emergencyContact.relationship;
    this.editEmergencyPhone = this.profile.emergencyContact.phone;
    this.editSecondaryName = this.profile.secondaryContact.name;
    this.editSecondaryRelationship = this.profile.secondaryContact.relationship;
    this.editSecondaryPhone = this.profile.secondaryContact.phone;
    this.editBloodType = this.profile.bloodType;
    this.editAllergies = this.profile.allergies.join(', ');
    this.editMedicalSummary = this.profile.medicalSummary;
  }

  private validateEditProfile(): string {
    if (this.editFullName.trim().length === 0) {
      return 'Name is required.';
    }
    if (this.editHomeAddress.trim().length === 0) {
      return 'Home address is required.';
    }
    if (this.editPreferredLanguage.trim().length === 0) {
      return 'Preferred language is required.';
    }
    if (this.editEmergencyName.trim().length === 0 || this.editEmergencyRelationship.trim().length === 0 ||
      this.editEmergencyPhone.trim().length === 0) {
      return 'Primary emergency contact is required.';
    }
    if (this.editMedicalSummary.trim().length === 0) {
      return 'Medical summary is required.';
    }

    const hasSecondaryAny: boolean = this.editSecondaryName.trim().length > 0 ||
      this.editSecondaryRelationship.trim().length > 0 ||
      this.editSecondaryPhone.trim().length > 0;
    const hasSecondaryAll: boolean = this.editSecondaryName.trim().length > 0 &&
      this.editSecondaryRelationship.trim().length > 0 &&
      this.editSecondaryPhone.trim().length > 0;
    if (hasSecondaryAny && !hasSecondaryAll) {
      return 'Complete all secondary contact fields or clear all.';
    }

    return '';
  }

  private saveEditedProfile(): void {
    const validationMessage: string = this.validateEditProfile();
    if (validationMessage.length > 0) {
      this.editMessage = validationMessage;
      return;
    }

    const parsedAllergies: string[] = this.editAllergies.split(',')
      .map((item: string) => item.trim())
      .filter((item: string) => item.length > 0)
      .slice(0, 8);

    this.profile = {
      fullName: this.editFullName.trim(),
      homeAddress: this.editHomeAddress.trim(),
      preferredLanguage: this.editPreferredLanguage.trim(),
      emergencyContact: {
        name: this.editEmergencyName.trim(),
        relationship: this.editEmergencyRelationship.trim(),
        phone: this.editEmergencyPhone.trim()
      },
      secondaryContact: {
        name: this.editSecondaryName.trim().length > 0 ? this.editSecondaryName.trim() : 'Not set',
        relationship: this.editSecondaryRelationship.trim().length > 0 ? this.editSecondaryRelationship.trim() : 'Not set',
        phone: this.editSecondaryPhone.trim().length > 0 ? this.editSecondaryPhone.trim() : 'Not set'
      },
      bloodType: this.editBloodType.trim().length > 0 ? this.editBloodType.trim() : 'Unknown',
      allergies: parsedAllergies.length > 0 ? parsedAllergies : ['None listed'],
      medicalSummary: this.editMedicalSummary.trim(),
      lastUpdatedAt: new Date().toISOString().slice(0, 16).replace('T', ' ')
    };

    this.storedProfileText = JSON.stringify(this.profile);
    this.syncDisplayFieldsFromProfile();
    this.profileRenderVersion++;
    this.editMessage = 'Profile saved successfully.';
  }

  private restoreEditFields(): void {
    this.syncEditFieldsFromProfile();
    this.editMessage = 'Unsaved changes were cleared.';
  }

  private callContact(name: string, phone: string): void {
    const digits: string = phone.replace(/[^\d+]/g, '');
    promptAction.showToast({
      message: 'Calling ' + name + ' (' + phone + ')...',
      duration: 3000
    });
    call.makeCall(digits).catch((err: BusinessError) => {
      console.error('Failed to initiate call: ' + JSON.stringify(err));
    });
  }

  @Builder
  buildPageTitle(title: string) {
    Text(title)
      .fontSize(10)
      .fontColor('#888888')
      .fontWeight(FontWeight.Medium)
      .textAlign(TextAlign.Center)
      .width('100%')
      .margin({ bottom: 2 })
  }

  @Builder
  buildPrimaryContactRow() {
    Column({ space: 2 }) {
      Text('Primary')
        .fontSize(11)
        .fontColor('#4ADE80')
        .fontWeight(FontWeight.Bold)
        .textAlign(TextAlign.Center)
        .width('100%')

      Text(this.displayEmergencyName)
        .fontSize(15)
        .fontColor('#FFFFFF')
        .fontWeight(FontWeight.Medium)
        .textAlign(TextAlign.Center)
        .width('100%')
        .maxLines(1)
        .textOverflow({ overflow: TextOverflow.Ellipsis })

      Text(this.displayEmergencyRelationship)
        .fontSize(11)
        .fontColor('#9CA3AF')
        .textAlign(TextAlign.Center)
        .width('100%')
        .maxLines(1)
        .textOverflow({ overflow: TextOverflow.Ellipsis })

      Text(this.displayEmergencyPhone)
        .fontSize(12)
        .fontColor('#FFFFFF')
        .fontWeight(FontWeight.Bold)
        .width('100%')
        .textAlign(TextAlign.Center)
    }
    .width('100%')
    .alignItems(HorizontalAlign.Center)
    .padding({ left: 10, right: 10, top: 6, bottom: 6 })
    .backgroundColor('#1A1A1A')
    .borderRadius(12)
    .onClick(() => {
      this.callContact(this.displayEmergencyName, this.displayEmergencyPhone);
    })
  }

  @Builder
  buildSecondaryContactRow() {
    Column({ space: 2 }) {
      Text('Secondary')
        .fontSize(11)
        .fontColor('#9CA3AF')
        .fontWeight(FontWeight.Bold)
        .textAlign(TextAlign.Center)
        .width('100%')

      Text(this.displaySecondaryName)
        .fontSize(15)
        .fontColor('#FFFFFF')
        .fontWeight(FontWeight.Medium)
        .textAlign(TextAlign.Center)
        .width('100%')
        .maxLines(1)
        .textOverflow({ overflow: TextOverflow.Ellipsis })

      Text(this.displaySecondaryRelationship)
        .fontSize(11)
        .fontColor('#9CA3AF')
        .textAlign(TextAlign.Center)
        .width('100%')
        .maxLines(1)
        .textOverflow({ overflow: TextOverflow.Ellipsis })

      Text(this.displaySecondaryPhone)
        .fontSize(12)
        .fontColor('#FFFFFF')
        .fontWeight(FontWeight.Bold)
        .width('100%')
        .textAlign(TextAlign.Center)
    }
    .width('100%')
    .alignItems(HorizontalAlign.Center)
    .padding({ left: 10, right: 10, top: 6, bottom: 6 })
    .backgroundColor('#1A1A1A')
    .borderRadius(12)
    .onClick(() => {
      this.callContact(this.displaySecondaryName, this.displaySecondaryPhone);
    })
  }

  @Builder
  buildHelpPage() {
    Column({ space: 6 }) {
      this.buildPageTitle('ANCHOR COMMUNITY CARD')

      Text(this.displayFullName)
        .fontSize(28)
        .fontColor('#FFFFFF')
        .fontWeight(FontWeight.Bold)
        .textAlign(TextAlign.Center)
        .maxLines(1)
        .textOverflow({ overflow: TextOverflow.Ellipsis })
        .width('100%')

      Text('If disoriented, please call family.')
        .fontSize(13)
        .fontColor('#9CA3AF')
        .textAlign(TextAlign.Center)
        .lineHeight(18)
        .maxLines(2)
        .width('100%')

      Column({ space: 2 }) {
        Text('CALL PRIMARY CONTACT')
          .fontSize(11)
          .fontColor('#4ADE80')
          .fontWeight(FontWeight.Bold)

        Text(this.displayEmergencyName)
          .fontSize(16)
          .fontColor('#FFFFFF')
          .fontWeight(FontWeight.Bold)
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })

        Text(this.displayEmergencyRelationship + '  |  ' + this.displayEmergencyPhone)
          .fontSize(13)
          .fontColor('#9CA3AF')
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
      }
      .width('100%')
      .padding({ left: 10, right: 10, top: 8, bottom: 8 })
      .backgroundColor('#1A1A1A')
      .borderRadius(14)
      .onClick(() => {
        this.callContact(this.displayEmergencyName, this.displayEmergencyPhone);
      })

      Text('Swipe for contacts and medical info')
        .fontSize(10)
        .fontColor('#9CA3AF')
        .textAlign(TextAlign.Center)
        .width('100%')
        .maxLines(1)
        .textOverflow({ overflow: TextOverflow.Ellipsis })
    }
    .width(this.PAGE_WIDTH)
    .height(this.PAGE_HEIGHT)
    .justifyContent(FlexAlign.Center)
    .alignItems(HorizontalAlign.Center)
  }

  @Builder
  buildContactsPage() {
    Column({ space: 6 }) {
      this.buildPageTitle('EMERGENCY CONTACTS')
      this.buildPrimaryContactRow()
      this.buildSecondaryContactRow()

      Text('Resident: ' + this.displayFullName)
        .fontSize(10)
        .fontColor('#9CA3AF')
        .textAlign(TextAlign.Center)
        .maxLines(1)
        .textOverflow({ overflow: TextOverflow.Ellipsis })
        .width('100%')

      // Depend on this counter to guarantee page refresh after save.
      Text('')
        .fontSize(1)
        .fontColor(Color.Transparent)
        .height(1)
        .width(1)
        .opacity(this.profileRenderVersion % 2)
    }
    .width(this.PAGE_WIDTH)
    .height(this.PAGE_HEIGHT)
    .justifyContent(FlexAlign.Center)
    .alignItems(HorizontalAlign.Center)
  }

  @Builder
  buildInfoTile(label: string, value: string, accent: string = '#FFFFFF') {
    Column({ space: 2 }) {
      Text(label)
        .fontSize(9)
        .fontColor('#9CA3AF')
      Text(value)
        .fontSize(14)
        .fontColor(accent)
        .fontWeight(FontWeight.Bold)
        .maxLines(1)
        .textOverflow({ overflow: TextOverflow.Ellipsis })
    }
    .layoutWeight(1)
    .alignItems(HorizontalAlign.Start)
    .padding({ left: 7, right: 7, top: 6, bottom: 6 })
    .backgroundColor('#252525')
    .borderRadius(10)
  }

  @Builder
  buildInfoBlock(title: string, value: string, maxLines: number = 2) {
    Column({ space: 3 }) {
      Text(title)
        .fontSize(9)
        .fontColor('#9CA3AF')
        .fontWeight(FontWeight.Medium)
      Text(value)
        .fontSize(11)
        .fontColor('#FFFFFF')
        .lineHeight(15)
        .maxLines(maxLines)
        .textOverflow({ overflow: TextOverflow.Ellipsis })
    }
    .width('100%')
    .padding({ left: 7, right: 7, top: 6, bottom: 6 })
    .backgroundColor('#1A1A1A')
    .borderRadius(10)
  }

  @Builder
  buildAllergyChips() {
    Flex({ wrap: FlexWrap.Wrap, justifyContent: FlexAlign.Start }) {
      ForEach(this.profile.allergies, (item: string) => {
        Text(item)
          .fontSize(10)
          .fontColor('#FFFFFF')
          .fontWeight(FontWeight.Medium)
          .padding({ left: 7, right: 7, top: 2, bottom: 2 })
          .backgroundColor('#252525')
          .borderRadius(8)
          .margin({ right: 5, bottom: 5 })
      })
    }
    .width('100%')
  }

  @Builder
  buildMedicalPage() {
    Column({ space: 2 }) {
      this.buildPageTitle('MEDICAL AND INFO')

      Column({ space: 3 }) {
        Text() {
          Span('Blood Type: ')
            .fontColor('#77D7FF')
            .fontWeight(FontWeight.Bold)
          Span(this.displayBloodType)
            .fontColor('#FFFFFF')
        }
        .fontSize(10)
        .lineHeight(13)
        .maxLines(1)
        .textOverflow({ overflow: TextOverflow.Ellipsis })
        .width('100%')

        Text() {
          Span('Language: ')
            .fontColor('#8FF0C4')
            .fontWeight(FontWeight.Bold)
          Span(this.displayPreferredLanguage)
            .fontColor('#FFFFFF')
        }
        .fontSize(10)
        .lineHeight(13)
        .maxLines(1)
        .textOverflow({ overflow: TextOverflow.Ellipsis })
        .width('100%')

        Text() {
          Span('Allergies: ')
            .fontColor('#FFD37A')
            .fontWeight(FontWeight.Bold)
          Span(this.displayAllergiesText)
            .fontColor('#FFFFFF')
        }
        .fontSize(10)
        .lineHeight(13)
        .maxLines(1)
        .textOverflow({ overflow: TextOverflow.Ellipsis })
        .width('100%')

        Text() {
          Span('Address: ')
            .fontColor('#C6B0FF')
            .fontWeight(FontWeight.Bold)
          Span(this.displayHomeAddress)
            .fontColor('#FFFFFF')
        }
        .fontSize(10)
        .lineHeight(13)
        .maxLines(2)
        .textOverflow({ overflow: TextOverflow.Ellipsis })
        .width('100%')

        Text() {
          Span('Medical: ')
            .fontColor('#FF9FA0')
            .fontWeight(FontWeight.Bold)
          Span(this.displayMedicalSummary)
            .fontColor('#FFFFFF')
        }
        .fontSize(10)
        .lineHeight(13)
        .maxLines(2)
        .textOverflow({ overflow: TextOverflow.Ellipsis })
        .width('100%')

        Text() {
          Span('Updated: ')
            .fontColor('#9CA3AF')
            .fontWeight(FontWeight.Bold)
          Span(this.displayLastUpdatedAt)
            .fontColor('#9CA3AF')
        }
        .fontSize(9)
        .lineHeight(12)
        .maxLines(1)
        .textOverflow({ overflow: TextOverflow.Ellipsis })
        .width('100%')
      }
      .width('100%')
      .padding({ left: 3, right: 3, top: 2, bottom: 24 })
    }
    .width(this.PAGE_WIDTH)
    .height(this.MEDICAL_PAGE_HEIGHT)
    .justifyContent(FlexAlign.Start)
    .alignItems(HorizontalAlign.Center)
    .padding({ top: 10, bottom: 4 })
  }

  @Builder
  buildEditField(label: string, value: string, placeholder: string, onChangeHandler: (value: string) => void,
    multiline: boolean = false) {
    Column({ space: 2 }) {
      Text(label)
        .fontSize(10)
        .fontColor('#9CA3AF')
        .width('100%')

      if (multiline) {
        TextInput({ text: value, placeholder: placeholder })
          .fontSize(11)
          .height(44)
          .textAlign(TextAlign.Center)
          .padding({ left: 10, right: 10 })
          .backgroundColor('#252525')
          .borderRadius(8)
          .width('100%')
          .onChange((input: string) => {
            onChangeHandler(input);
          })
      } else {
        TextInput({ text: value, placeholder: placeholder })
          .fontSize(11)
          .height(32)
          .textAlign(TextAlign.Center)
          .padding({ left: 10, right: 10 })
          .backgroundColor('#252525')
          .borderRadius(8)
          .width('100%')
          .onChange((input: string) => {
            onChangeHandler(input);
          })
      }
    }
    .width('100%')
  }

  @Builder
  buildEditPage() {
    Column({ space: 4 }) {
      this.buildPageTitle('EDIT PROFILE')

      Scroll() {
        Column({ space: 6 }) {
          this.buildEditField('Name*', this.editFullName, 'Resident full name', (v: string) => {
            this.editFullName = v;
          })
          this.buildEditField('Home Address*', this.editHomeAddress, 'Home address', (v: string) => {
            this.editHomeAddress = v;
          }, true)
          this.buildEditField('Preferred Language*', this.editPreferredLanguage, 'English, Mandarin', (v: string) => {
            this.editPreferredLanguage = v;
          })
          this.buildEditField('Emergency Name*', this.editEmergencyName, 'Primary contact name', (v: string) => {
            this.editEmergencyName = v;
          })
          this.buildEditField('Emergency Relationship*', this.editEmergencyRelationship, 'Son, Daughter, Caregiver', (v: string) => {
            this.editEmergencyRelationship = v;
          })
          this.buildEditField('Emergency Phone*', this.editEmergencyPhone, '+65 xxxx xxxx', (v: string) => {
            this.editEmergencyPhone = v;
          })
          this.buildEditField('Secondary Name', this.editSecondaryName, 'Optional', (v: string) => {
            this.editSecondaryName = v;
          })
          this.buildEditField('Secondary Relationship', this.editSecondaryRelationship, 'Optional', (v: string) => {
            this.editSecondaryRelationship = v;
          })
          this.buildEditField('Secondary Phone', this.editSecondaryPhone, 'Optional', (v: string) => {
            this.editSecondaryPhone = v;
          })
          this.buildEditField('Blood Type', this.editBloodType, 'O+, A-, Unknown', (v: string) => {
            this.editBloodType = v;
          })
          this.buildEditField('Allergies', this.editAllergies, 'Comma separated', (v: string) => {
            this.editAllergies = v;
          })
          this.buildEditField('Medical Summary*', this.editMedicalSummary, 'Short summary', (v: string) => {
            this.editMedicalSummary = v;
          }, true)

          Text(this.editMessage)
            .fontSize(10)
            .fontColor('#4ADE80')
            .maxLines(2)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
            .width('100%')
            .margin({ bottom: 8 })

          Blank()
            .height(4)
        }
        .width('100%')
      }
      .width('100%')
      .layoutWeight(1)
      .scrollBar(BarState.Off)

      Row({ space: 8 }) {
        Button('Save')
          .fontSize(12)
          .height(32)
          .backgroundColor('#4ADE80')
          .onClick(() => {
            this.saveEditedProfile();
          })
        Button('Reset')
          .fontSize(12)
          .height(32)
          .backgroundColor('#252525')
          .onClick(() => {
            this.restoreEditFields();
          })
      }
      .width('100%')
      .justifyContent(FlexAlign.Center)
      .margin({ top: 4, bottom: 18 })
    }
    .width(this.EDIT_PAGE_WIDTH)
    .height(this.PAGE_HEIGHT)
    .justifyContent(FlexAlign.Start)
    .alignItems(HorizontalAlign.Center)
  }

  build() {
    Column() {

      // Back button — identical style to the BackButton() used on all other pages.
      Text('< Back')
        .fontSize(11)
        .fontColor('#666666')
        .padding({ top: 3, bottom: 3, left: 6, right: 6 })
        .alignSelf(ItemAlign.Center)
        .onClick(() => { router.back(); })

      // Swiper fills remaining height below the back button.
      Swiper() {
        this.buildHelpPage()
        this.buildContactsPage()
        this.buildMedicalPage()
        this.buildEditPage()
      }
      .width('100%')
      .layoutWeight(1)
      .indicator(true)
      .loop(false)
      .autoPlay(false)
      .duration(260)

    }
    .width('100%')
    .height('100%')
    .backgroundColor('#0D0D0D')
  }
}