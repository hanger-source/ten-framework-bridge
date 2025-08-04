import React from "react";
import { Provider } from "react-redux";
import { makeStore } from "./store";
import { Toaster } from "sonner";
import Home from "./components/Home";
import "./App.css";

const store = makeStore();

function App() {
  return (
    <Provider store={store}>
      <Home />
      <Toaster />
    </Provider>
  );
}

export default App;