# The EPUB reader is our own parser behind an interface

Import uses a small ZIP and XML reader that implements `EpubTextExtractor`. Readium Kotlin is the likely replacement when the app needs a full navigator, but it is not on the MVP classpath.

Readium pulls in a large Android stack and makes the import path hard to test on the JVM. The domain only sees extracted blocks and anchors. The original file is stored unchanged. Markup is dropped and text is NFC-normalized; those steps are extraction rules, not a license for the model to rewrite the book.
