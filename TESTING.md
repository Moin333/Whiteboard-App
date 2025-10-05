# Testing Checklist

## Functional Tests

- [ ] Drawing with pen works smoothly
- [ ] Eraser removes paths correctly
- [ ] Shapes draw with proper preview
- [ ] Text tool opens dialog
- [ ] Undo/redo works correctly
- [ ] Pan and zoom are smooth
- [ ] Selection tool selects objects
- [ ] Export generates files correctly
- [ ] Save/load preserves all objects
- [ ] Auto-save creates backups

## Performance Tests

- [ ] 60 FPS with 100+ objects
- [ ] No memory leaks after 30 min use
- [ ] Smooth zoom at all levels
- [ ] Fast session load (< 2 seconds)

## Edge Cases

- [ ] Handle out of memory gracefully
- [ ] Empty canvas save/load
- [ ] Very large strokes
- [ ] Rapid tool switching
- [ ] Export with no objects

## Device Tests

- [ ] Phone (5-6 inch)
- [ ] Multi-touch gestures