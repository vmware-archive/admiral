/*
 * Copyright (c) 2016 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package cmd

import (
	"fmt"
	"strings"

	"admiral/images"

	"github.com/spf13/cobra"
)

func init() {
	RootCmd.AddCommand(searchCmd)
}

var searchCmd = &cobra.Command{
	Use:   "search [IMAGE-NAME]",
	Short: "Search for image from which you can provision container.",
	Long:  "Search for image from which you can provision container.",

	Run: func(cmd *cobra.Command, args []string) {
		RunSearch(args)
	},
}

func RunSearch(args []string) {
	if len(args) < 1 {
		images.PrintPopular()
		return
	}
	query := strings.Join(args, " ")
	il := &images.ImagesList{}
	count, err := il.QueryImages(query)
	if err != nil {
		fmt.Println(err)
		return
	}
	if count < 1 {
		fmt.Println("n/a")
		return
	}
	il.Print()
}
