## Custom theme for OpenMRS ID

This is a custom theme for OpenMRS ID based on the [Keywind](https://github.com/lukin/keywind) theme for Keycloak.

![Preview](./preview.jpeg)

### Add a custom announcement

To add a custom announcement, you can edit the [`template.ftl`](theme/keywind/login/template.ftl) file.

ex:
```
<@alert.kw color="warning">This is a custom message!</@alert.kw>
```

![Preview](./announcement.jpeg)
