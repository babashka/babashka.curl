# Changes

## 0.0.2

- [#35](https://github.com/babashka/babashka.curl/issues/35): use
  `--data-binary` when sending files or streams

## 0.0.1

- [#16](https://github.com/babashka/babashka.curl/issues/16): BREAKING!
  Exceptional status codes or nonzero `curl` exit codes will throw exceptions by
  default.

- [#9](https://github.com/babashka/babashka.curl/issues/9): BREAKING! Functions
  like `get`, `post`, etc. now always return a map with `:status`, `:body`, and
  `:headers`.
