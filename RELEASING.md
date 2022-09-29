Releasing
===

1. Merge release-please PR(`chore(main): release X.Y.Z`)
2. `release` workflow will create a release tag, and that will trigger `publish-maven` workflow.
    - new release version is taken from the tag.
3. Wait until `publish-maven` workflow completes. The workflow automatically release the artifacts to Maven Central.

## Manual release

`publish-maven` workflow can trigger manually.

1. Navigate to [`Actions > publish-maven`](https://github.com/bucketeer-io/android-client-sdk/actions/workflows/publish-maven.yml)
2. Click `Run workflow` and choose the target ref.
3. Enter target release version to `Bucketeer version`.
    - The format is `vX.Y.Z`(e.g. `v2.0.1`).
    - You can also include suffix like `v3.0.0-SNAPSHOT` or `v3.0.0-RC1`
4. Hit the `Run workflow` button, and the rest is handled by the workflow.
