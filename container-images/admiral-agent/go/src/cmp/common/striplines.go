//Package striplines strips runs of consecutive empty lines from an output stream.
package common

// http://stackoverflow.com/questions/28353313/strip-consecutive-empty-lines-in-a-golang-writer

import (
  "io"
  "strings"
)

// Striplines wraps an output stream, stripping runs of consecutive empty lines.
// You must call Flush before the output stream will be complete.
// Implements io.WriteCloser, Writer, Closer.
type Striplines struct {
  Writer   io.Writer
  lastLine []byte
  currentLine []byte
}

func (w *Striplines) Write(p []byte) (int, error) {
  totalN := 0
  s := string(p)
  if !strings.Contains(s, "\n") {
    w.currentLine = append(w.currentLine, p...)
    return 0, nil
  }
  cur := string(append(w.currentLine, p...))
  lastN := strings.LastIndex(cur, "\n")
  s = cur[:lastN]
  for _, line := range strings.Split(s, "\n") {
    n, err := w.writeLn(line + "\n")
    w.lastLine = []byte(line)
    if err != nil {
      return totalN, err
    }
    totalN += n
  }
  rem := cur[(lastN + 1):]
  w.currentLine = []byte(rem)
  return totalN, nil
}

// Close flushes the last of the output into the underlying writer.
func (w *Striplines) Close() error {
  _, err := w.writeLn(string(w.currentLine))
  return err
}

func (w *Striplines) writeLn(line string) (n int, err error) {
  if strings.TrimSpace(string(w.lastLine)) == "" && strings.TrimSpace(line) == "" {
    return 0, nil
  } else {
    return w.Writer.Write([]byte(line))
  }
}