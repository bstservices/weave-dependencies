# weave-dependencies

This plugin is for weaving binary aspects into binary dependencies, then adding
the woven dependencies into your runtime classpath. Usually, such dependencies
are woven at load time. However, this incurs some start up cost, which can be
avoided by weaving at compile time.

You can add this plugin to your project by adding it to your plugins block:

```groovy
plugins {
    id "services.bst.weave-dependencies" version "0.1.2"
}
```

The default configuration is:

```groovy
weaveDependencies {
    aspectJVersion = "1.9.2"
    classesToExclude = []
    ajcXlint = "ignore"
}
```

This plugin defines two configurations: `aspects` and `weave`. Packages that
`aspects` depend on will be woven into the intransitive dependencies of
`weave`. Additionally, the dependencies of both `aspects` and `weave` are added
to compile.

For example, to weave Kamon's instrumentation into Akka, you would add these
dependencies:

```groovy
dependencies {
    aspects group: 'io.kamon', name: 'kamon-core_2.12', version: '1.1.2'
    aspects group: 'io.kamon', name: 'kamon-akka-2.5_2.12', version: '1.1.2'
    weave group: 'com.typesafe.akka', name: 'akka-actor_2.12', version: '2.5.20'
}
```

## Copying

Copyright 2019 BST Event Services, LLC.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
