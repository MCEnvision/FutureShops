package com.enviouse.futureshops;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DependencyAlertRecordTest {
    @Test
    void recordClassifiesEveryOpenAdvisory() throws Exception {
        String record = Files.readString(Path.of(
                "docs/security/dependency-alerts-3.0-beta.4.md"));
        List<String> advisories = List.of(
                "GHSA-558v-64gr-wgg4",
                "GHSA-c653-97m9-rcg9",
                "GHSA-w573-9ffj-6ff9",
                "GHSA-x4gw-5cx5-pgmh",
                "GHSA-3qp7-7mw8-wx86",
                "GHSA-mj4r-2hfc-f8p6",
                "GHSA-6hg6-v5c8-fphq",
                "GHSA-3pxv-7cmr-fjr4",
                "GHSA-6fmv-xxpf-w3cw",
                "GHSA-vc5p-v9hr-52mj",
                "GHSA-3p8m-j85q-pgmj",
                "GHSA-j288-q9x7-2f5v",
                "GHSA-389x-839f-4rhx",
                "GHSA-xq3w-v528-46rv",
                "GHSA-78wr-2p64-hpwj",
                "GHSA-4g9r-vxhx-9pgx",
                "GHSA-4265-ccf5-phj5",
                "GHSA-5mg8-w23w-74h3",
                "GHSA-7g45-4rm6-3mm3",
                "GHSA-6mjq-h674-j845",
                "GHSA-mc84-pj99-q6hh",
                "GHSA-xqfj-vm6h-2x34",
                "GHSA-crv7-7245-f45f",
                "GHSA-7hfm-57qf-j43q",
                "GHSA-53x6-4x5p-rrvv");

        assertTrue(advisories.stream().allMatch(record::contains));
    }
}
