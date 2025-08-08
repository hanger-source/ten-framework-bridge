class RMSProcessor extends AudioWorkletProcessor {
  process(inputs) {
    const input = inputs[0][0];
    if (input) {
      let sum = 0;
      for (let i = 0; i < input.length; i++) {
        sum += input[i] * input[i];
      }
      const rms = Math.sqrt(sum / input.length);
      this.port.postMessage(rms);
    }
    return true;
  }
}
registerProcessor('rms-processor', RMSProcessor); 