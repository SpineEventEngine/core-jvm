### Live Templates

This directory contains two live template groups:

1. `Spine.xml`: shortcuts for the repeated patterns used in the framework.
2. `User.xml`: a single shortcut to generate TODO comments.

### Instlallation

Live templates are not picked up by IDEA automatically. They should be added manually.
In order to add these templates, perform the following steps:

1. Copy `*.xml` files from this directory to `templates` directory in the IntelliJ IDEA 
   [settings folder][settings_folder].
2. Restart IntelliJ IDEA: `File -> Invalidate Caches -> Just restart`.
3. Go to `Preferences -> Editor -> Live Templates`.
4. Verify `User` and `Spine` template groups are present.

[settings_folder]: https://www.jetbrains.com/help/idea/directories-used-by-the-ide-to-store-settings-caches-plugins-and-logs.html#config-directory

### Configuring `User.todo` template

1. Open the corresponding template: `Preferences -> Editor -> Live Templates -> User.todo`.
2. Click on `Edit variables`.
3. Set `USER` variable to your domain email address without `@teamdev.com` ending.  For example,
   for `jack.sparrow@teamdev.com` use the follwoing expression `"jack.sparrow"`.
4. Verify that the template generates expected comments: `// TODO:2022-11-03:jack.sparrow: <...>`.
