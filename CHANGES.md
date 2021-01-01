# Changes

- [#16](https://github.com/babashka/babashka.curl/issues/16): BREAKING!
  Exceptional status codes or nonzero `curl` exit codes will throw exceptions by
  default.

- [#9](https://github.com/babashka/babashka.curl/issues/9): BREAKING! Functions
  like `get`, `post`, etc. now always return a map with `:status`, `:body`, and
  `:headers`.
