# Changelog

## [2.1.7](https://github.com/bucketeer-io/android-client-sdk/compare/v2.1.6...v2.1.7) (2024-06-06)


### Bug Fixes

* concurrentModificationException while initializationing ([#179](https://github.com/bucketeer-io/android-client-sdk/issues/179)) ([610bef0](https://github.com/bucketeer-io/android-client-sdk/commit/610bef08e0973016ed960cdd56437a545906b122))


### Miscellaneous

* **sample:** enable switching user and tag during runtime. ([#172](https://github.com/bucketeer-io/android-client-sdk/issues/172)) ([a61ef0a](https://github.com/bucketeer-io/android-client-sdk/commit/a61ef0af10cd94c91e428e706422962b7e0c2576))


### Build System

* **deps:** update all non-major dependencies ([#161](https://github.com/bucketeer-io/android-client-sdk/issues/161)) ([8aa2f7c](https://github.com/bucketeer-io/android-client-sdk/commit/8aa2f7cf07194586286698d85d357b1f90298e93))
* **deps:** update all non-major dependencies ([#168](https://github.com/bucketeer-io/android-client-sdk/issues/168)) ([35721d5](https://github.com/bucketeer-io/android-client-sdk/commit/35721d525c180a0d1d7f298675d01e8294a588da))
* **deps:** update all non-major dependencies ([#173](https://github.com/bucketeer-io/android-client-sdk/issues/173)) ([c6bcd55](https://github.com/bucketeer-io/android-client-sdk/commit/c6bcd5572076bcebb9a46c736f604d0c64d3a359))
* **deps:** update plugin ksp to v2.0.0-1.0.22 ([#176](https://github.com/bucketeer-io/android-client-sdk/issues/176)) ([f06f789](https://github.com/bucketeer-io/android-client-sdk/commit/f06f7896516a158726fd5a3c9ce8ddf90f574154))
* **deps:** update to kotlin 2.0.0 ([#174](https://github.com/bucketeer-io/android-client-sdk/issues/174)) ([e957644](https://github.com/bucketeer-io/android-client-sdk/commit/e957644ed2c9d75b09780099f7e81fd525ac2f80))

## [2.1.6](https://github.com/bucketeer-io/android-client-sdk/compare/v2.1.5...v2.1.6) (2024-04-23)


### Miscellaneous

* add the source ID to the register events API ([#165](https://github.com/bucketeer-io/android-client-sdk/issues/165)) ([c6df4d6](https://github.com/bucketeer-io/android-client-sdk/commit/c6df4d6113000546af9d7c8fa40ccd595dd1ebd5))


### Bug Fixes

* metric event reporting source ID as UNKNOWN  ([#164](https://github.com/bucketeer-io/android-client-sdk/issues/164)) ([9d6dc38](https://github.com/bucketeer-io/android-client-sdk/commit/9d6dc38fec4e8e79d3d66f1127d5d017c6bdb682))

## [2.1.5](https://github.com/bucketeer-io/android-client-sdk/compare/v2.1.4...v2.1.5) (2024-03-21)


### Build System

* **deps:** update all non-major dependencies ([#135](https://github.com/bucketeer-io/android-client-sdk/issues/135)) ([1aa5ce8](https://github.com/bucketeer-io/android-client-sdk/commit/1aa5ce841b91bbf2068a266bd8e9cf9ed49bf248))
* **deps:** update plugin publish to v0.28.0 ([#120](https://github.com/bucketeer-io/android-client-sdk/issues/120)) ([31bb8ae](https://github.com/bucketeer-io/android-client-sdk/commit/31bb8ae17f18609853d1f957f8242effe80d94c3))


### Miscellaneous

* update error metrics report ([#134](https://github.com/bucketeer-io/android-client-sdk/issues/134)) ([f548c41](https://github.com/bucketeer-io/android-client-sdk/commit/f548c41fcff45317c53b6a6aaf6bad871b8db6f8))


### Bug Fixes

* missing R8 / ProGuard file ([#141](https://github.com/bucketeer-io/android-client-sdk/issues/141)) ([f3db4db](https://github.com/bucketeer-io/android-client-sdk/commit/f3db4db89b17ca46fc0392fa663eee05ee41e33f))
* potential memory leak when destroying the SDK client ([#142](https://github.com/bucketeer-io/android-client-sdk/issues/142)) ([5270ed8](https://github.com/bucketeer-io/android-client-sdk/commit/5270ed8f55dee3ef46c3ee9d2931c36525fb88d9))

## [2.1.4](https://github.com/bucketeer-io/android-client-sdk/compare/v2.1.3...v2.1.4) (2024-03-06)

### Miscellaneous

- lose the main thread dependency in the initialize and destroy implementation ([#129](https://github.com/bucketeer-io/android-client-sdk/issues/129)) ([748736b](https://github.com/bucketeer-io/android-client-sdk/commit/748736b07ccbd5c478a4ecd2876f2be6667ff56c))

### Bug Fixes

- npe while initializing the sdk using sqlite-framework 2.3.0 ([#130](https://github.com/bucketeer-io/android-client-sdk/issues/130)) ([00a159c](https://github.com/bucketeer-io/android-client-sdk/commit/00a159ca190ca6718c14218d6814be7444ea760f))

### Build System

- **deps:** update google-github-actions/release-please-action action to v3.7.13 ([#117](https://github.com/bucketeer-io/android-client-sdk/issues/117)) ([1fc8ba4](https://github.com/bucketeer-io/android-client-sdk/commit/1fc8ba4931f6a0c6e9194a4125b474d3b20cb59d))

## [2.1.3](https://github.com/bucketeer-io/android-client-sdk/compare/v2.1.2...v2.1.3) (2023-10-17)

### Build System

- **deps:** update plugin publish to v0.25.3 ([#84](https://github.com/bucketeer-io/android-client-sdk/issues/84)) ([df9f747](https://github.com/bucketeer-io/android-client-sdk/commit/df9f74760fd182735237f7d6c45cebe9d3444117))
- **deps:** update actions/checkout action to v4 ([#98](https://github.com/bucketeer-io/android-client-sdk/issues/98)) ([a3a6700](https://github.com/bucketeer-io/android-client-sdk/commit/a3a6700a6160659685b7e901e9b062cf5f8b368d))
- **deps:** update actions/setup-java action to v3.13.0 ([#102](https://github.com/bucketeer-io/android-client-sdk/issues/102)) ([9fb41a7](https://github.com/bucketeer-io/android-client-sdk/commit/9fb41a7819bf145addb96f7c301531f01b0dbfb4))

### Miscellaneous

- add sdk version to the network requests ([#99](https://github.com/bucketeer-io/android-client-sdk/issues/99)) ([6c6fa90](https://github.com/bucketeer-io/android-client-sdk/commit/6c6fa90b8df78800a45ffeb3db990ba72f7aac38))
- add the current timeout setting in the TimeoutErrorMetricsEvent ([#91](https://github.com/bucketeer-io/android-client-sdk/issues/91)) ([cd0fbde](https://github.com/bucketeer-io/android-client-sdk/commit/cd0fbde4d5697ad981fa9a1dd0cca6bd54872e00))
- change timeout type from float to double ([#114](https://github.com/bucketeer-io/android-client-sdk/issues/114)) ([b8054d7](https://github.com/bucketeer-io/android-client-sdk/commit/b8054d7134b3f597c1b45d9da726dd615ee46092))

### Bug Fixes

- latency seconds field in metrics event ([#90](https://github.com/bucketeer-io/android-client-sdk/issues/90)) ([ff43158](https://github.com/bucketeer-io/android-client-sdk/commit/ff43158ee84541a4184d1787023214bb047d0b75))

### Performance Improvements

- improve the network traffic and response time ([#88](https://github.com/bucketeer-io/android-client-sdk/issues/88)) ([ee154b4](https://github.com/bucketeer-io/android-client-sdk/commit/ee154b49f3e9cea7d71cf12bdf5c5982b7fd5131))

### Refactoring

- evaluation data layer ([#89](https://github.com/bucketeer-io/android-client-sdk/issues/89)) ([6f110d3](https://github.com/bucketeer-io/android-client-sdk/commit/6f110d36f323137f4a9956980c39646f6df6df76))

## [2.1.2](https://github.com/bucketeer-io/android-client-sdk/compare/v2.1.1...v2.1.2) (2023-07-12)

### Bug Fixes

- json data expection while converting reason type ([#79](https://github.com/bucketeer-io/android-client-sdk/issues/79)) ([dc4aba9](https://github.com/bucketeer-io/android-client-sdk/commit/dc4aba9a4c0671bc990f211f5bdbb1a209c118d6))

## [2.1.1](https://github.com/bucketeer-io/android-client-sdk/compare/v2.1.0...v2.1.1) (2023-07-03)

### Miscellaneous

- add variation name property to BKTEvaluation ([#73](https://github.com/bucketeer-io/android-client-sdk/issues/73)) ([87f7e07](https://github.com/bucketeer-io/android-client-sdk/commit/87f7e07e14a13dd40ac5d8fe9a39685912a8eedb))

### Build System

- **deps:** update androidxlifecycle to v2.6.1 ([#78](https://github.com/bucketeer-io/android-client-sdk/issues/78)) ([d1fc03a](https://github.com/bucketeer-io/android-client-sdk/commit/d1fc03adf09ad482ff07ecf9ef1e662e48fcb479))
- **deps:** update dependency gradle to v7.6.2 ([#75](https://github.com/bucketeer-io/android-client-sdk/issues/75)) ([1d3d6a9](https://github.com/bucketeer-io/android-client-sdk/commit/1d3d6a97f58eef5157e8c1c2b118deed4b6f756a))
- **deps:** update plugin kotlinter to v3.15.0 ([#77](https://github.com/bucketeer-io/android-client-sdk/issues/77)) ([60ef581](https://github.com/bucketeer-io/android-client-sdk/commit/60ef581f85c664cea660b7deb2619f1816558b47))
- **deps:** update plugin publish to v0.25.2 ([#72](https://github.com/bucketeer-io/android-client-sdk/issues/72)) ([58e33cb](https://github.com/bucketeer-io/android-client-sdk/commit/58e33cb6bc8b94b30bec91c7bb8a61023a9bee8e))

## [2.1.0](https://github.com/bucketeer-io/android-client-sdk/compare/v2.0.0...v2.1.0) (2023-06-13)

### Features

- add metadata ([#35](https://github.com/bucketeer-io/android-client-sdk/issues/35)) ([8769920](https://github.com/bucketeer-io/android-client-sdk/commit/8769920d0c2bf4be34f37fadc1fcc152d3140846))
- add new metrics ([#64](https://github.com/bucketeer-io/android-client-sdk/issues/64)) ([61c574b](https://github.com/bucketeer-io/android-client-sdk/commit/61c574bce617577f18ca3656a73b4f9c1ebbb674))
- send sdk_version ([#33](https://github.com/bucketeer-io/android-client-sdk/issues/33)) ([f7fd846](https://github.com/bucketeer-io/android-client-sdk/commit/f7fd846b4821bb93564969a3117cef060a371306))

### Bug Fixes

- **deps:** update androidxtest to v1.5.0 ([#43](https://github.com/bucketeer-io/android-client-sdk/issues/43)) ([b994c73](https://github.com/bucketeer-io/android-client-sdk/commit/b994c7312cdf35941304b2d4cc8a637538adcb0e))
- **deps:** update dependency androidx.test:orchestrator to v1.4.2 ([#41](https://github.com/bucketeer-io/android-client-sdk/issues/41)) ([df7b911](https://github.com/bucketeer-io/android-client-sdk/commit/df7b91185ff0ef53bb87eea6cccbcad4ab7fbe3b))
- **deps:** update dependency androidx.test.ext:junit to v1.1.4 ([#40](https://github.com/bucketeer-io/android-client-sdk/issues/40)) ([054922b](https://github.com/bucketeer-io/android-client-sdk/commit/054922bf0d137526687ce071cb6077c200a81033))
- **deps:** update dependency com.google.android.material:material to v1.7.0 ([#44](https://github.com/bucketeer-io/android-client-sdk/issues/44)) ([ad5b896](https://github.com/bucketeer-io/android-client-sdk/commit/ad5b8962a81ffd2cc7f6c14125748a32395ed0a1))
- **deps:** update dependency com.google.testparameterinjector:test-parameter-injector to v1.10 ([#39](https://github.com/bucketeer-io/android-client-sdk/issues/39)) ([a1cc332](https://github.com/bucketeer-io/android-client-sdk/commit/a1cc3322d685bcf1de93e9b5513f816944aed7f1))
- **deps:** update dependency com.google.testparameterinjector:test-parameter-injector to v1.9 ([#32](https://github.com/bucketeer-io/android-client-sdk/issues/32)) ([bf6933f](https://github.com/bucketeer-io/android-client-sdk/commit/bf6933f46bd17e2bca6888960f47fcf74003dbef))
- **deps:** update dependency org.robolectric:robolectric to v4.9 ([#34](https://github.com/bucketeer-io/android-client-sdk/issues/34)) ([1cd4239](https://github.com/bucketeer-io/android-client-sdk/commit/1cd4239ba36f7f7db3b8e5bf2e7a6767d1dd528b))
- **deps:** update dependency org.robolectric:robolectric to v4.9.2 ([#42](https://github.com/bucketeer-io/android-client-sdk/issues/42)) ([9d17344](https://github.com/bucketeer-io/android-client-sdk/commit/9d1734432ff496dbccd4fc4866346794760f828b))

### Miscellaneous

- change not to duplicate the same metric events ([#68](https://github.com/bucketeer-io/android-client-sdk/issues/68)) ([82f89f7](https://github.com/bucketeer-io/android-client-sdk/commit/82f89f7cebca9972a5e76b25308722f65f9680c7))
- change the APIs request/response format ([#63](https://github.com/bucketeer-io/android-client-sdk/issues/63)) ([3fca036](https://github.com/bucketeer-io/android-client-sdk/commit/3fca0362e03e0fd6f49f7b811ce461a64946bc8d))
- **deps:** update actions/setup-java action to v3.11.0 ([#61](https://github.com/bucketeer-io/android-client-sdk/issues/61)) ([e6616a3](https://github.com/bucketeer-io/android-client-sdk/commit/e6616a36e83d5165496920da434853d734a6d8aa))
- **deps:** update actions/setup-java action to v3.6.0 ([#29](https://github.com/bucketeer-io/android-client-sdk/issues/29)) ([7ba510a](https://github.com/bucketeer-io/android-client-sdk/commit/7ba510ae1c8a4126dab95185317cde14383baeb8))
- **deps:** update actions/setup-java action to v3.9.0 ([#38](https://github.com/bucketeer-io/android-client-sdk/issues/38)) ([47b226d](https://github.com/bucketeer-io/android-client-sdk/commit/47b226d74859d162b2fa3447c15653705b89bb53))
- **deps:** update amannn/action-semantic-pull-request action to v5 ([#31](https://github.com/bucketeer-io/android-client-sdk/issues/31)) ([69d45eb](https://github.com/bucketeer-io/android-client-sdk/commit/69d45ebcb7e6888800e3a3e1e294d1385e32a128))
- **deps:** update dependency androidx.test.ext:junit to v1.1.5 ([#54](https://github.com/bucketeer-io/android-client-sdk/issues/54)) ([6d11126](https://github.com/bucketeer-io/android-client-sdk/commit/6d111260ca9a9ff79465de96265ea634c38f7376))
- **deps:** update dependency gradle to v7.6 ([#45](https://github.com/bucketeer-io/android-client-sdk/issues/45)) ([79880d2](https://github.com/bucketeer-io/android-client-sdk/commit/79880d25ca09a42c58d0715e0e65441309324deb))
- **deps:** update dependency gradle to v7.6.1 ([#57](https://github.com/bucketeer-io/android-client-sdk/issues/57)) ([f86eaa1](https://github.com/bucketeer-io/android-client-sdk/commit/f86eaa1fb6735bce434fa3218a8ed8c7cfaad3f6))
- **deps:** update google-github-actions/release-please-action action to v3.6.0 ([#30](https://github.com/bucketeer-io/android-client-sdk/issues/30)) ([c4b35f4](https://github.com/bucketeer-io/android-client-sdk/commit/c4b35f49bc98c37e4f83e14dbfc6abfb6e8a6646))
- **deps:** update google-github-actions/release-please-action action to v3.7.3 ([#53](https://github.com/bucketeer-io/android-client-sdk/issues/53)) ([a1832d0](https://github.com/bucketeer-io/android-client-sdk/commit/a1832d0cea9b05bd018f0277fa596c3ab0272308))
- **deps:** update google-github-actions/release-please-action action to v3.7.4 ([#58](https://github.com/bucketeer-io/android-client-sdk/issues/58)) ([c0be7f6](https://github.com/bucketeer-io/android-client-sdk/commit/c0be7f69519d4dc53079af277394e5ad1d35e045))
- **deps:** update google-github-actions/release-please-action action to v3.7.8 ([#60](https://github.com/bucketeer-io/android-client-sdk/issues/60)) ([17cb789](https://github.com/bucketeer-io/android-client-sdk/commit/17cb789fd068f637099827374600cf3eb3e6d723))
- **deps:** update kotlin monorepo to v1.7.22 ([#37](https://github.com/bucketeer-io/android-client-sdk/issues/37)) ([f59acbc](https://github.com/bucketeer-io/android-client-sdk/commit/f59acbc4a44d28a361551b399c77dfbe3935b95e))
- **deps:** update plugin kotlin-dokka to v1.7.20 ([#47](https://github.com/bucketeer-io/android-client-sdk/issues/47)) ([4244df1](https://github.com/bucketeer-io/android-client-sdk/commit/4244df110757108a9a9ebab0897a1fd4c67d92ad))
- **deps:** update plugin kotlinter to v3.13.0 ([#46](https://github.com/bucketeer-io/android-client-sdk/issues/46)) ([b4d7a53](https://github.com/bucketeer-io/android-client-sdk/commit/b4d7a532be7b1b7bb74b5fc375ff202a8b941f1c))
- **deps:** update plugin publish to v0.24.0 ([#50](https://github.com/bucketeer-io/android-client-sdk/issues/50)) ([56a2cad](https://github.com/bucketeer-io/android-client-sdk/commit/56a2cad4912fa9dcf1e6b19d8d5acfe1af442df5))
- force to re-evaluate the user when the custom attributes change ([#59](https://github.com/bucketeer-io/android-client-sdk/issues/59)) ([73508d1](https://github.com/bucketeer-io/android-client-sdk/commit/73508d1642da3c6db052f66a7471032aad56c269))
- set default value for track interface ([#62](https://github.com/bucketeer-io/android-client-sdk/issues/62)) ([5544bbf](https://github.com/bucketeer-io/android-client-sdk/commit/5544bbfb5dd127a8d2058f5112734543270b2156))

### Build System

- **deps:** update agp to v7.3.1 ([#26](https://github.com/bucketeer-io/android-client-sdk/issues/26)) ([30c70a5](https://github.com/bucketeer-io/android-client-sdk/commit/30c70a5efde54ebdc8b3652fd5c7a9592b7d707b))
- **deps:** update kotlin and AGP version ([#70](https://github.com/bucketeer-io/android-client-sdk/issues/70)) ([7b09892](https://github.com/bucketeer-io/android-client-sdk/commit/7b0989223acee12ba69402395411139fdd73f044))

## 2.0.0 (2022-10-04)

### Features

- evaluation update listener ([#5](https://github.com/bucketeer-io/android-client-sdk/issues/5)) ([28c137c](https://github.com/bucketeer-io/android-client-sdk/commit/28c137c184053405c759c7e30c912cf27e9fc119))

### Bug Fixes

- **deps:** update dependency androidx.appcompat:appcompat to v1.5.1 ([#21](https://github.com/bucketeer-io/android-client-sdk/issues/21)) ([0d69b07](https://github.com/bucketeer-io/android-client-sdk/commit/0d69b0765c56059bdd809bb6a9f171a8ce4fa3ef))

### Miscellaneous

- change renovate to check for update monthly ([#22](https://github.com/bucketeer-io/android-client-sdk/issues/22)) ([dcd5a8e](https://github.com/bucketeer-io/android-client-sdk/commit/dcd5a8e37d693f17eedf83858d86ca825c1976b8))
- **deps:** add renovate.json ([#4](https://github.com/bucketeer-io/android-client-sdk/issues/4)) ([9a92fba](https://github.com/bucketeer-io/android-client-sdk/commit/9a92fba6c9a6f103589ebd9188958a62455c0298))
- **deps:** update actions/setup-java action to v3.5.1 ([#15](https://github.com/bucketeer-io/android-client-sdk/issues/15)) ([83ca4d5](https://github.com/bucketeer-io/android-client-sdk/commit/83ca4d5cac652f39af2a15c6b26fd956c4af5d73))
- **deps:** update agp to v7.3.0 ([#19](https://github.com/bucketeer-io/android-client-sdk/issues/19)) ([e3085ca](https://github.com/bucketeer-io/android-client-sdk/commit/e3085caa30cfc6a376ac878e4d5967461f9b5552))
- **deps:** update dependency gradle to v7.5.1 ([#20](https://github.com/bucketeer-io/android-client-sdk/issues/20)) ([70a92cf](https://github.com/bucketeer-io/android-client-sdk/commit/70a92cfd4f15eba00b8d4197e317eed343334471))
- **deps:** update kotlin to v1.7.20 ([#16](https://github.com/bucketeer-io/android-client-sdk/issues/16)) ([5226188](https://github.com/bucketeer-io/android-client-sdk/commit/522618823b3196568a44c24caf5914264d064f80))
- **deps:** update plugin ksp to v1.7.20-1.0.6 ([#18](https://github.com/bucketeer-io/android-client-sdk/issues/18)) ([a4eb543](https://github.com/bucketeer-io/android-client-sdk/commit/a4eb543f45722b681084036504ef69ae1a1aa649))
- remove unused dependencies ([#9](https://github.com/bucketeer-io/android-client-sdk/issues/9)) ([3dc4361](https://github.com/bucketeer-io/android-client-sdk/commit/3dc4361839dad1e7ce61c814cd4cf88dd12ce364))
- rename endpoint config to apiEndpoint ([#14](https://github.com/bucketeer-io/android-client-sdk/issues/14)) ([0bf267a](https://github.com/bucketeer-io/android-client-sdk/commit/0bf267a3468a67a2fc48536e64748166903a8203))
- rename to setUserAttributes interface to updateUserAttributes ([#13](https://github.com/bucketeer-io/android-client-sdk/issues/13)) ([b6c05e4](https://github.com/bucketeer-io/android-client-sdk/commit/b6c05e4d5d065eec905f242addb1a1ed3543a256))
- update POM urls ([#24](https://github.com/bucketeer-io/android-client-sdk/issues/24)) ([06fd0d9](https://github.com/bucketeer-io/android-client-sdk/commit/06fd0d91595e26581b9522a8ac04ae5b970a4b03))
