# Change Log
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/)
and this project adheres to [Semantic Versioning](http://semver.org/).

## [Unreleased]
### Added

### Changed
- use \_boot\_config.edn instead of web.xml.edn as hidden config file
- rename boot_gae/ to templates/
- :gae map in build.boot reorganized (BREAKING)
- move :module from :gae in build.boot to appengine.edn
- remove support for servlet apps, only service-based apps allowed

## [0.1.0]
### Added
- This CHANGELOG file to hopefully serve as an evolving example of a standardized open source project CHANGELOG.

### Changed
- :gae :app :dir uses relative path

[Unreleased]: https://github.com/migae/boot-gae/tree/master
[0.1.0]: https://github.com/migae/boot-gae/tree/master
