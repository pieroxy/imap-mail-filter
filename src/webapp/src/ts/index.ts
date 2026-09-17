import m from "mithril";

const HelloWorld = {
  view: () => m("h1", "Hello World"),
};

m.mount(document.getElementById("app")!, HelloWorld);
