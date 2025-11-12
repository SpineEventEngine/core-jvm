# Welcome to Spine CoreJvm Library

[![Ubuntu build][ubuntu-build-badge]][gh-actions]
[![codecov.io](https://codecov.io/github/SpineEventEngine/core-java/coverage.svg?branch=master)](https://codecov.io/github/SpineEventEngine/core-java?branch=master) &nbsp;
[![license](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat)](http://www.apache.org/licenses/LICENSE-2.0)

[gh-actions]: https://github.com/SpineEventEngine/core-java/actions
[ubuntu-build-badge]: https://github.com/SpineEventEngine/core-java/actions/workflows/build-on-ubuntu.yml/badge.svg

[Spine Event Engine][spine-site] is a Java/Kotlin framework for building Event Sourcing and CQRS
applications that are accessed by clients built with TypeStript, JavaScript, Java Nano (Android),
Dart, and Java/Kotlin.

## Releases

The project is under active ongoing development. 
You are welcome to experiment and [provide your feedback][email-developers].

The latest stable version is [1.9.0][latest-release].

## Contents

This repository contains the code of:
 - core types;
 - client API;
 - server API;
 - testing utilities for the client- and server-side code.
  

## Quick start and examples

Please see the [“Quick Start” guide][quick-start] which goes through a Hello World project showing
how to create a Spine-based project.
 
More introductory application examples are available from
the [Spine Examples][spine-examples] GitHub organization.

## Building from sources

Starting version `2.0.0-SNAPSHOT.210` building Spine modules requires JDK 17.

Versions of the v1.x family could be built with JDK 8.

Gradle is used as a build and dependency management system.

This repository uses [configuration files][config] shared across several Spine libs.
They are plugged in as a Git submodule. Use the following command to initialize it:

```sh
git submodule update --init --recursive
```  

Also, a script called `pull` in the root of the `core-jvm` project which can be used
to update to the latest version of the configuration files.

## Important warnings

* The code annotated with `@Internal` are not parts of public API of the framework. 
  Therefore, such API should not be used from the outside of the framework.

* The public API marked as `@Experimental` may be used at own risk; it can change at any time, 
  and has no guarantee of API stability or backward-compatibility.

* The API annotated with `@SPI` is for those who intend to extend the framework, 
  or provide custom storage implementations. 

If you need to use API with one of these annotations, please [contact us][email-developers].

[email-developers]: mailto:developers@spine.io
[latest-release]: https://github.com/SpineEventEngine/core-java/releases/tag/v1.9.0
[spine-site]: https://spine.io/
[quick-start]: https://spine.io/docs/quick-start
[spine-examples]: https://github.com/spine-examples
[todo-list]: https://github.com/spine-examples/todo-list
[v2]: https://github.com/orgs/SpineEventEngine/projects/13
[config]: https://github.com/SpineEventEngine/config/
