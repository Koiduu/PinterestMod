// Turns a YouTube link on .video into an embed. The editor sets data-youtube.
export function renderVideo(box) {
  box.querySelector("iframe")?.remove();
  const id = youtubeId(box.dataset.youtube || "");
  if (!id) return;
  const frame = document.createElement("iframe");
  frame.src = `https://www.youtube-nocookie.com/embed/${id}?rel=0`;
  frame.title = "How to use PinSpo";
  frame.allow = "accelerometer; encrypted-media; gyroscope; picture-in-picture; fullscreen";
  frame.allowFullscreen = true;
  frame.dataset.generated = "";
  box.append(frame);
}

export function youtubeId(url) {
  const m = url.match(/(?:youtu\.be\/|v=|embed\/|shorts\/)([\w-]{11})/);
  return m ? m[1] : "";
}

document.querySelectorAll(".video").forEach(renderVideo);

if (new URLSearchParams(location.search).has("edit")) {
  import("./editor.js");
}
